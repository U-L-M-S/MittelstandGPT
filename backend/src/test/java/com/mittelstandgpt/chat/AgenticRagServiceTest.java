package com.mittelstandgpt.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mittelstandgpt.chat.RelevanceGrader.GradeResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * Unit tests for the bounded corrective-retrieval loop, with the retriever,
 * grader and answerer mocked. These verify the orchestration contract from
 * Workstream A: single-shot when grading is off, multi-hop accumulation across
 * documents, the hard hop bound, and the abstain path.
 */
class AgenticRagServiceTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final RelevanceGrader grader = mock(RelevanceGrader.class);
    private final GroundedAnswerService answerer = mock(GroundedAnswerService.class);

    private AgenticRagService service(int maxHops, int topK, boolean grading) {
        RagProperties p = new RagProperties();
        p.setMaxHops(maxHops);
        p.setTopK(topK);
        p.setGradingEnabled(grading);
        return new AgenticRagService(retrieval, grader, answerer, p);
    }

    private static Document doc(String text, String source, Integer page) {
        Map<String, Object> meta =
                page == null ? Map.of("source", source) : Map.of("source", source, "page", page);
        return Document.builder().text(text).metadata(meta).build();
    }

    @Test
    void gradingDisabled_runsSingleHopWithoutGrading() {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(doc("a", "a.pdf", 1)));

        var outcome = service(3, 4, false).retrieveCorrectively("frage");

        assertThat(outcome.hops()).isEqualTo(1);
        assertThat(outcome.chunks()).hasSize(1);
        verify(retrieval, times(1)).search(eq("frage"), eq(4));
        verifyNoInteractions(grader);
    }

    @Test
    void multiHop_combinesChunksFromTwoDocuments() {
        Document a = doc("Urlaubsanspruch betraegt 30 Tage", "handbuch.pdf", 3);
        Document b = doc("Antrag laeuft ueber das HR-Portal", "portal.pdf", 1);
        when(retrieval.search(eq("frage"), anyInt())).thenReturn(List.of(a));
        when(retrieval.search(eq("wie beantragen"), anyInt())).thenReturn(List.of(b));
        // First hop finds doc A but is not sufficient -> follow-up; second hop finds B.
        when(grader.grade(eq("frage"), anyList()))
                .thenReturn(new GradeResult(List.of(a), false, "wie beantragen"))
                .thenReturn(new GradeResult(List.of(b), true, ""));

        var outcome = service(3, 4, true).retrieveCorrectively("frage");

        assertThat(outcome.hops()).isEqualTo(2);
        assertThat(outcome.queries()).containsExactly("frage", "wie beantragen");
        assertThat(outcome.chunks()).containsExactlyInAnyOrder(a, b);
    }

    @Test
    void loop_isBoundedByMaxHops() {
        Document x = doc("teilweise relevant", "x.pdf", 1);
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(x));
        // Never sufficient and always asks for more -> would loop forever if unbounded.
        when(grader.grade(anyString(), anyList()))
                .thenReturn(new GradeResult(List.of(x), false, "mehr"));

        var outcome = service(3, 4, true).retrieveCorrectively("frage");

        assertThat(outcome.hops()).isEqualTo(3);
        verify(retrieval, times(3)).search(anyString(), anyInt());
        verify(grader, times(3)).grade(anyString(), anyList());
    }

    @Test
    void noRelevantChunks_yieldsEmptyOutcomeAndSentinel() {
        Document noise = doc("voellig irrelevant", "noise.pdf", 1);
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(noise));
        when(grader.grade(anyString(), anyList()))
                .thenReturn(new GradeResult(List.of(), false, "")); // nothing relevant, no follow-up

        AgenticRagService svc = service(3, 4, true);

        var outcome = svc.retrieveCorrectively("frage");
        assertThat(outcome.chunks()).isEmpty();
        assertThat(outcome.hops()).isEqualTo(1);

        ChatResponse response = svc.ask("frage");
        assertThat(response.answer()).isEqualTo(GroundedAnswerService.NO_ANSWER);
        assertThat(response.sources()).isEmpty();
        verify(answerer, never()).answer(anyString(), anyList());
    }

    @Test
    void ask_groundedAnswerCarriesSources() {
        Document a = doc("Ein Fakt aus dem Dokument", "a.pdf", 2);
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(a));
        when(grader.grade(anyString(), anyList())).thenReturn(new GradeResult(List.of(a), true, ""));
        when(answerer.answer(eq("frage"), anyList())).thenReturn("Die belegte Antwort.");
        when(answerer.sources(anyList())).thenReturn(List.of(new Source("a.pdf", 2)));

        ChatResponse response = service(3, 4, true).ask("frage");

        assertThat(response.answer()).isEqualTo("Die belegte Antwort.");
        assertThat(response.sources()).containsExactly(new Source("a.pdf", 2));
    }

    @Test
    void ask_sentinelAnswerOmitsSources() {
        Document a = doc("Ein Fakt", "a.pdf", 2);
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(a));
        when(grader.grade(anyString(), anyList())).thenReturn(new GradeResult(List.of(a), true, ""));
        when(answerer.answer(anyString(), anyList())).thenReturn(GroundedAnswerService.NO_ANSWER);

        ChatResponse response = service(3, 4, true).ask("frage");

        assertThat(response.answer()).isEqualTo(GroundedAnswerService.NO_ANSWER);
        assertThat(response.sources()).isEmpty();
        verify(answerer, never()).sources(anyList());
    }
}
