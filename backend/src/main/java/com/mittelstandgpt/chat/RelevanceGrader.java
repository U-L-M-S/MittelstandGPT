package com.mittelstandgpt.chat;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Corrective-RAG grading. Judges which retrieved chunks are relevant to the
 * question, whether they are sufficient to answer it, and — if not — proposes a
 * reformulated follow-up query for the next retrieval hop.
 */
public interface RelevanceGrader {

    GradeResult grade(String question, List<Document> retrieved);

    /**
     * @param relevant the retrieved chunks judged relevant to the question
     * @param sufficient whether {@code relevant} fully answers the question
     * @param followUpQuery a targeted query for the next hop when not sufficient
     *     (blank/null when no further retrieval is warranted)
     */
    record GradeResult(List<Document> relevant, boolean sufficient, String followUpQuery) {

        /** Fail-open result: keep everything and stop the loop. */
        public static GradeResult keepAll(List<Document> retrieved) {
            return new GradeResult(List.copyOf(retrieved), true, null);
        }
    }
}
