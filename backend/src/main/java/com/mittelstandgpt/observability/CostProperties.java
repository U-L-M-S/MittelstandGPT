package com.mittelstandgpt.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-1000-token prices used to derive an estimated request cost. Bound from
 * {@code mi.cost.*}. Defaults are 0.0 (the local model is free); set real prices
 * per model/profile (e.g. Azure OpenAI) via the environment.
 */
@ConfigurationProperties(prefix = "mi.cost")
public class CostProperties {

    private double inputPer1k = 0.0;
    private double outputPer1k = 0.0;
    private String currency = "EUR";

    public double getInputPer1k() {
        return inputPer1k;
    }

    public void setInputPer1k(double inputPer1k) {
        this.inputPer1k = inputPer1k;
    }

    public double getOutputPer1k() {
        return outputPer1k;
    }

    public void setOutputPer1k(double outputPer1k) {
        this.outputPer1k = outputPer1k;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
