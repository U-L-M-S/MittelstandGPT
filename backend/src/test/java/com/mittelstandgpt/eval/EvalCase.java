package com.mittelstandgpt.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** One row of the golden evaluation dataset ({@code eval/dataset.jsonl}). */
public record EvalCase(
        String id,
        String question,
        @JsonProperty("expected_facts") List<String> expectedFacts,
        @JsonProperty("expected_sources") List<String> expectedSources,
        @JsonProperty("should_answer") boolean shouldAnswer,
        String category) {}
