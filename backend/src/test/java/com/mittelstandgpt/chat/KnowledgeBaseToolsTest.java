package com.mittelstandgpt.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mittelstandgpt.document.DocumentInfo;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/** Unit tests for the model-callable knowledge-base tools (mocked retrieval). */
class KnowledgeBaseToolsTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final KnowledgeBaseTools tools = new KnowledgeBaseTools(retrieval);

    @Test
    void searchKnowledgeBase_mapsChunksWithSourceAndPage() {
        when(retrieval.search("urlaub", 2))
                .thenReturn(
                        List.of(
                                Document.builder()
                                        .text("30 Tage")
                                        .metadata(Map.of("source", "hb.pdf", "page", 3))
                                        .build()));

        var chunks = tools.searchKnowledgeBase("urlaub", 2);

        assertThat(chunks)
                .singleElement()
                .satisfies(
                        c -> {
                            assertThat(c.text()).isEqualTo("30 Tage");
                            assertThat(c.source()).isEqualTo("hb.pdf");
                            assertThat(c.page()).isEqualTo(3);
                        });
    }

    @Test
    void searchKnowledgeBase_defaultsTopKWhenNull() {
        when(retrieval.search(anyString(), eq(4))).thenReturn(List.of());

        tools.searchKnowledgeBase("q", null);

        verify(retrieval).search("q", 4);
    }

    @Test
    void listDocuments_mapsRegistryEntries() {
        when(retrieval.documents())
                .thenReturn(
                        List.of(new DocumentInfo("a.pdf", "application/pdf", 100L, 5, Instant.now())));

        var docs = tools.listDocuments();

        assertThat(docs)
                .singleElement()
                .satisfies(
                        d -> {
                            assertThat(d.filename()).isEqualTo("a.pdf");
                            assertThat(d.chunks()).isEqualTo(5);
                        });
    }

    @Test
    void searchWithinDocument_delegatesWithFilename() {
        when(retrieval.searchWithin("a.pdf", "q", 4)).thenReturn(List.of());

        tools.searchWithinDocument("a.pdf", "q");

        verify(retrieval).searchWithin("a.pdf", "q", 4);
    }
}
