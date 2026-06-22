package com.mittelstandgpt.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.Usage;

/** Unit tests for the token + estimated-cost Micrometer metrics. */
class TokenCostMetricsTest {

    private static Usage usage(int prompt, int completion) {
        Usage u = mock(Usage.class);
        when(u.getPromptTokens()).thenReturn(prompt);
        when(u.getCompletionTokens()).thenReturn(completion);
        return u;
    }

    @Test
    void recordsTokenCountersAndDerivedCost() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CostProperties cost = new CostProperties();
        cost.setInputPer1k(2.0);
        cost.setOutputPer1k(8.0);
        TokenCostMetrics metrics = new TokenCostMetrics(registry, cost);

        metrics.record(usage(1000, 500));

        assertThat(registry.get("mittelstandgpt.tokens").tag("type", "input").counter().count())
                .isEqualTo(1000.0);
        assertThat(registry.get("mittelstandgpt.tokens").tag("type", "output").counter().count())
                .isEqualTo(500.0);
        // 1000/1000*2.0 + 500/1000*8.0 = 2.0 + 4.0 = 6.0
        assertThat(registry.get("mittelstandgpt.cost.total").counter().count())
                .isCloseTo(6.0, within(1e-9));
    }

    @Test
    void recordIsNullSafe() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenCostMetrics metrics = new TokenCostMetrics(registry, new CostProperties());

        metrics.record(null); // must not throw

        assertThat(registry.get("mittelstandgpt.tokens").tag("type", "input").counter().count())
                .isEqualTo(0.0);
    }

    @Test
    void freeLocalModelRecordsZeroCost() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TokenCostMetrics metrics = new TokenCostMetrics(registry, new CostProperties()); // prices 0.0

        metrics.record(usage(1234, 567));

        assertThat(registry.get("mittelstandgpt.cost.total").counter().count()).isEqualTo(0.0);
        assertThat(registry.get("mittelstandgpt.tokens").tag("type", "input").counter().count())
                .isEqualTo(1234.0);
    }
}
