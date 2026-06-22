package com.mittelstandgpt.chat;

import com.mittelstandgpt.document.DocumentIngestionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Agentic, self-correcting retrieval (Corrective-RAG). Replaces the single fixed
 * {@code similaritySearch(topK)} with a bounded loop:
 *
 * <pre>
 *   retrieve → grade relevance → (reformulate &amp; re-retrieve) → answer
 * </pre>
 *
 * The loop is deterministic and logged, and the hop count is bounded by
 * {@code mi.rag.max-hops}. The relevance grader decides which chunks count, when
 * the accumulated context is sufficient, and — when it is not — supplies a
 * targeted follow-up query for the next hop (this is what lets a question that
 * spans two documents pull chunks from both). Grading can be turned off
 * ({@code mi.rag.grading-enabled=false}), which collapses the loop to the
 * original single-shot retrieval.
 *
 * <p>Sources handed back to the UI are exactly the chunks carried through the
 * loop into the grounded answer, so citation stays accurate across hops. The
 * strict-grounding contract, the German no-answer sentinel and the SSE streaming
 * contract are preserved via {@link GroundedAnswerService}.
 */
@Service
public class AgenticRagService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRagService.class);

    private final RetrievalService retrieval;
    private final RelevanceGrader grader;
    private final GroundedAnswerService answerer;
    private final RagProperties props;

    public AgenticRagService(
            RetrievalService retrieval,
            RelevanceGrader grader,
            GroundedAnswerService answerer,
            RagProperties props) {
        this.retrieval = retrieval;
        this.grader = grader;
        this.answerer = answerer;
        this.props = props;
    }

    /** Answer a question synchronously. */
    public ChatResponse ask(String question) {
        RetrievalOutcome outcome = retrieveCorrectively(question);
        if (outcome.chunks().isEmpty()) {
            return new ChatResponse(GroundedAnswerService.NO_ANSWER, List.of());
        }
        String answer = answerer.answer(question, outcome.chunks());
        // Don't cite sources for a "not found" answer — it would be misleading.
        List<Source> sources =
                GroundedAnswerService.isNoAnswer(answer)
                        ? List.of()
                        : answerer.sources(outcome.chunks());
        return new ChatResponse(answer, sources);
    }

    /** Answer a question as a token stream, with the sources resolved up front. */
    public StreamResult stream(String question) {
        RetrievalOutcome outcome = retrieveCorrectively(question);
        if (outcome.chunks().isEmpty()) {
            return new StreamResult(List.of(), Flux.just(GroundedAnswerService.NO_ANSWER));
        }
        Flux<String> tokens = answerer.streamAnswer(question, outcome.chunks());
        return new StreamResult(answerer.sources(outcome.chunks()), tokens);
    }

    /**
     * The bounded corrective-retrieval loop. Package-visible so it can be unit
     * tested in isolation with a mocked retriever and grader.
     */
    RetrievalOutcome retrieveCorrectively(String question) {
        int maxHops = Math.max(1, props.getMaxHops());
        int topK = props.getTopK();
        // Preserve first-seen order while de-duplicating chunks carried across hops.
        Map<String, Document> selected = new LinkedHashMap<>();
        List<String> queries = new ArrayList<>();
        String query = question;

        for (int hop = 0; hop < maxHops; hop++) {
            queries.add(query);
            List<Document> retrieved = retrieval.search(query, topK);
            log.info(
                    "RAG hop {}/{}: query=\"{}\" -> {} chunk(s)",
                    hop + 1,
                    maxHops,
                    query,
                    retrieved.size());

            if (!props.isGradingEnabled()) {
                retrieved.forEach(d -> selected.putIfAbsent(key(d), d));
                break;
            }

            RelevanceGrader.GradeResult graded = grader.grade(question, retrieved);
            graded.relevant().forEach(d -> selected.putIfAbsent(key(d), d));

            String followUp = graded.followUpQuery();
            if (graded.sufficient() || followUp == null || followUp.isBlank()) {
                log.info(
                        "RAG stop after hop {}: sufficient={}, relevantChunks={}",
                        hop + 1,
                        graded.sufficient(),
                        selected.size());
                break;
            }
            query = followUp;
        }

        List<Document> chunks = new ArrayList<>(selected.values());
        log.info(
                "RAG done: {} hop(s), {} chunk(s) selected for grounding",
                queries.size(),
                chunks.size());
        return new RetrievalOutcome(chunks, queries.size(), queries);
    }

    /** Stable de-duplication key for a chunk (source + page + text hash). */
    private static String key(Document doc) {
        Object source = doc.getMetadata().get(DocumentIngestionService.META_SOURCE);
        Object page = doc.getMetadata().get(DocumentIngestionService.META_PAGE);
        String text = doc.getText();
        return source + "|" + page + "|" + (text == null ? "" : Integer.toHexString(text.hashCode()));
    }

    /** Sources known immediately, plus the answer streamed token by token. */
    public record StreamResult(List<Source> sources, Flux<String> answerTokens) {}

    /** The chunks selected for grounding, plus loop telemetry (hops, queries). */
    public record RetrievalOutcome(List<Document> chunks, int hops, List<String> queries) {}
}
