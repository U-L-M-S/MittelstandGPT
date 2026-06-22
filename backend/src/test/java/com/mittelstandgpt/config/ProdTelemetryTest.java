package com.mittelstandgpt.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * GDPR guard (Workstream C): under the {@code prod} profile, prompt/response
 * CONTENT must never be exported in telemetry. Spans, token counts and latency
 * are still emitted; only document/answer content is suppressed. Loads offline
 * (tracing export disabled for the test) and asserts the bound configuration.
 */
@SpringBootTest(
        properties = {
            "spring.ai.vectorstore.qdrant.initialize-schema=false",
            "app.qdrant.rest-url=http://localhost:6",
            // Keep the offline test free of OTLP export attempts; content gating is
            // independent of whether tracing export is on.
            "management.tracing.enabled=false"
        })
@ActiveProfiles({"local", "prod"})
class ProdTelemetryTest {

    @Autowired private Environment env;

    @Test
    void promptAndCompletionContentLoggingDisabledUnderProd() {
        assertThat(env.getProperty("spring.ai.chat.observations.log-prompt", Boolean.class))
                .as("prompt content export must be off in prod")
                .isFalse();
        assertThat(env.getProperty("spring.ai.chat.observations.log-completion", Boolean.class))
                .as("completion content export must be off in prod")
                .isFalse();
    }
}
