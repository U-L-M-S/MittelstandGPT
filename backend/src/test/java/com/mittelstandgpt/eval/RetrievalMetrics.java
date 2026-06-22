package com.mittelstandgpt.eval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure retrieval-quality metrics, computed from the ordered list of source ids of
 * the retrieved chunks versus the expected sources. Side-effect free and
 * hand-checkable — covered by unit tests so the harness's measurements are
 * trustworthy.
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {}

    /** 1.0 if any of the first {@code k} retrieved sources is expected, else 0.0. */
    public static double hitRateAtK(List<String> retrieved, Set<String> expected, int k) {
        if (expected.isEmpty()) {
            return 0.0;
        }
        int limit = Math.min(k, retrieved.size());
        for (int i = 0; i < limit; i++) {
            if (expected.contains(retrieved.get(i))) {
                return 1.0;
            }
        }
        return 0.0;
    }

    /** Reciprocal rank of the first expected source (0.0 if none was retrieved). */
    public static double reciprocalRank(List<String> retrieved, Set<String> expected) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (expected.contains(retrieved.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** Fraction of retrieved chunks whose source is expected (precision@retrieved). */
    public static double contextPrecision(List<String> retrieved, Set<String> expected) {
        if (retrieved.isEmpty()) {
            return 0.0;
        }
        long hits = retrieved.stream().filter(expected::contains).count();
        return (double) hits / retrieved.size();
    }

    /** Fraction of expected sources that appear among the retrieved chunks. */
    public static double contextRecall(List<String> retrieved, Set<String> expected) {
        if (expected.isEmpty()) {
            return 1.0;
        }
        Set<String> distinct = new LinkedHashSet<>(retrieved);
        long covered = expected.stream().filter(distinct::contains).count();
        return (double) covered / expected.size();
    }

    /** Mean of a list of doubles, or 0.0 when empty. */
    public static double mean(List<Double> values) {
        return values.isEmpty()
                ? 0.0
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
