package com.mittelstandgpt.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Hand-checked unit tests for the retrieval metric computations. */
class RetrievalMetricsTest {

    private static final double EPS = 1e-9;

    @Test
    void hitRateAtK_respectsTheCutoff() {
        List<String> retrieved = List.of("a.md", "b.md", "c.md", "d.md");
        Set<String> expected = Set.of("c.md");
        assertThat(RetrievalMetrics.hitRateAtK(retrieved, expected, 4)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.hitRateAtK(retrieved, expected, 3)).isEqualTo(1.0);
        assertThat(RetrievalMetrics.hitRateAtK(retrieved, expected, 2)).isEqualTo(0.0);
        assertThat(RetrievalMetrics.hitRateAtK(retrieved, Set.of(), 4)).isEqualTo(0.0);
    }

    @Test
    void reciprocalRank_usesFirstHitPosition() {
        assertThat(RetrievalMetrics.reciprocalRank(List.of("c.md"), Set.of("c.md"))).isEqualTo(1.0);
        assertThat(RetrievalMetrics.reciprocalRank(List.of("a.md", "c.md", "b.md"), Set.of("c.md")))
                .isCloseTo(0.5, within(EPS));
        assertThat(RetrievalMetrics.reciprocalRank(List.of("a.md", "b.md"), Set.of("c.md")))
                .isEqualTo(0.0);
    }

    @Test
    void contextPrecision_countsRelevantChunks() {
        assertThat(
                        RetrievalMetrics.contextPrecision(
                                List.of("a.md", "c.md", "c.md", "d.md"), Set.of("c.md")))
                .isCloseTo(0.5, within(EPS));
        assertThat(RetrievalMetrics.contextPrecision(List.of(), Set.of("c.md"))).isEqualTo(0.0);
    }

    @Test
    void contextRecall_coversExpectedSources() {
        assertThat(RetrievalMetrics.contextRecall(List.of("a.md", "c.md"), Set.of("c.md", "x.md")))
                .isCloseTo(0.5, within(EPS));
        assertThat(RetrievalMetrics.contextRecall(List.of("a.md"), Set.of())).isEqualTo(1.0);
        assertThat(RetrievalMetrics.contextRecall(List.of("c.md", "x.md"), Set.of("c.md", "x.md")))
                .isEqualTo(1.0);
    }

    @Test
    void mean_handlesEmpty() {
        assertThat(RetrievalMetrics.mean(List.of())).isEqualTo(0.0);
        assertThat(RetrievalMetrics.mean(List.of(1.0, 0.0))).isCloseTo(0.5, within(EPS));
    }
}
