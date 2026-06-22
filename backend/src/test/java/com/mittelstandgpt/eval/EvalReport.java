package com.mittelstandgpt.eval;

import java.util.List;

/** Serialized to {@code eval/report.json}: aggregate scores plus per-row detail. */
public record EvalReport(
        String timestamp,
        String chatModel,
        String embeddingModel,
        Thresholds thresholds,
        Aggregate aggregate,
        List<Row> rows) {

    public record Thresholds(double minFaithfulness, double minHitRate) {}

    public record Aggregate(
            int total,
            int answerable,
            int abstain,
            int answered,
            double hitRate,
            double mrr,
            double contextPrecision,
            double contextRecall,
            double faithfulness,
            double answerRelevance,
            double abstentionAccuracy,
            double factCoverage,
            boolean gatePassed,
            List<String> gateFailures) {}

    public record Row(
            String id,
            String category,
            boolean shouldAnswer,
            boolean answered,
            List<String> expectedSources,
            List<String> retrievedSources,
            int hops,
            double hitRate,
            double reciprocalRank,
            double contextPrecision,
            double contextRecall,
            Boolean faithful,
            Boolean relevant,
            double factCoverage,
            boolean abstentionCorrect,
            String answer) {}
}
