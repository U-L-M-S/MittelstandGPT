package com.mittelstandgpt.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

/**
 * Records LLM token usage and a derived estimated cost as Micrometer metrics,
 * scraped at {@code /actuator/prometheus}:
 *
 * <ul>
 *   <li>{@code mittelstandgpt.tokens{type="input|output"}} — token counters
 *   <li>{@code mittelstandgpt.cost.total{currency=...}} — estimated spend
 * </ul>
 *
 * Spans/traces (with per-stage token counts) go to Langfuse via OTLP; these
 * counters give a provider-agnostic, always-on cost view on the actuator.
 */
@Component
public class TokenCostMetrics {

    private final CostProperties cost;
    private final Counter inputTokens;
    private final Counter outputTokens;
    private final Counter costTotal;

    public TokenCostMetrics(MeterRegistry registry, CostProperties cost) {
        this.cost = cost;
        this.inputTokens =
                Counter.builder("mittelstandgpt.tokens")
                        .tag("type", "input")
                        .description("LLM input (prompt) tokens")
                        .register(registry);
        this.outputTokens =
                Counter.builder("mittelstandgpt.tokens")
                        .tag("type", "output")
                        .description("LLM output (completion) tokens")
                        .register(registry);
        this.costTotal =
                Counter.builder("mittelstandgpt.cost.total")
                        .tag("currency", cost.getCurrency())
                        .description("Estimated LLM cost derived from token usage")
                        .register(registry);
    }

    /** Records token usage and the derived estimated cost. Null-safe. */
    public void record(Usage usage) {
        if (usage == null) {
            return;
        }
        int in = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int out = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        if (in > 0) {
            inputTokens.increment(in);
        }
        if (out > 0) {
            outputTokens.increment(out);
        }
        double estimated = estimatedCost(in, out);
        if (estimated > 0) {
            costTotal.increment(estimated);
        }
    }

    /** Estimated cost for the given token counts, from the configured prices. */
    public double estimatedCost(int inputTokens, int outputTokens) {
        return inputTokens / 1000.0 * cost.getInputPer1k()
                + outputTokens / 1000.0 * cost.getOutputPer1k();
    }
}
