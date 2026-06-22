package com.mittelstandgpt.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * The build gate: fails when key quality metrics fall below their thresholds.
 * Thresholds default to the brief's values and are overridable via
 * {@code MI_EVAL_MIN_FAITHFULNESS} / {@code MI_EVAL_MIN_HITRATE} so they can be
 * tightened over time. Pure logic, unit tested.
 */
public record EvalGate(double minFaithfulness, double minHitRate) {

    public Result check(double faithfulness, double hitRate) {
        List<String> failures = new ArrayList<>();
        if (faithfulness < minFaithfulness) {
            failures.add(
                    "faithfulness %.3f < min %.3f".formatted(faithfulness, minFaithfulness));
        }
        if (hitRate < minHitRate) {
            failures.add("hit_rate@k %.3f < min %.3f".formatted(hitRate, minHitRate));
        }
        return new Result(failures.isEmpty(), List.copyOf(failures));
    }

    public record Result(boolean passed, List<String> failures) {}
}
