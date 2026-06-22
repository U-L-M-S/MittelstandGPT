package com.mittelstandgpt.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the agentic retrieval loop, bound from {@code mi.rag.*}. The
 * defaults are overridable via the environment variables the brief specifies:
 * {@code MI_RAG_MAX_HOPS}, {@code MI_RAG_TOPK}, {@code MI_RAG_GRADING_ENABLED}
 * (wired as explicit placeholders in application.yml).
 */
@ConfigurationProperties(prefix = "mi.rag")
public class RagProperties {

    /** Maximum number of retrieve → grade → reformulate hops per question. */
    private int maxHops = 3;

    /** Chunks fetched per similarity search. */
    private int topK = 4;

    /** Whether the LLM relevance-grading / corrective step runs. */
    private boolean gradingEnabled = true;

    /**
     * Optional minimum similarity score (0.0–1.0) a chunk must reach to be
     * retrieved. 0.0 disables filtering (accept all top-k). A deterministic
     * precision lever that complements the LLM corrective step.
     */
    private double similarityThreshold = 0.0;

    public int getMaxHops() {
        return maxHops;
    }

    public void setMaxHops(int maxHops) {
        this.maxHops = maxHops;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public boolean isGradingEnabled() {
        return gradingEnabled;
    }

    public void setGradingEnabled(boolean gradingEnabled) {
        this.gradingEnabled = gradingEnabled;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
