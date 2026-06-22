package com.mittelstandgpt.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Proves the gate logic is real: low metrics fail, good metrics pass. */
class EvalGateTest {

    private final EvalGate gate = new EvalGate(0.90, 0.80);

    @Test
    void passesWhenBothMetricsMeetThresholds() {
        EvalGate.Result result = gate.check(0.95, 0.85);
        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void failsOnLowFaithfulness() {
        EvalGate.Result result = gate.check(0.80, 0.95);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("faithfulness"));
    }

    @Test
    void failsOnLowHitRate() {
        // The "deliberate break" case: broken retrieval drives hit-rate to zero.
        EvalGate.Result result = gate.check(0.95, 0.0);
        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).anyMatch(f -> f.contains("hit_rate"));
    }

    @Test
    void reportsBothFailures() {
        assertThat(gate.check(0.10, 0.10).failures()).hasSize(2);
    }
}
