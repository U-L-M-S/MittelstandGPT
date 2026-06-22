package com.mittelstandgpt.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mittelstandgpt.observability.CostProperties;
import com.mittelstandgpt.observability.TokenCostMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

/** Unit tests for the grounding contract: source de-duplication and the sentinel. */
class GroundedAnswerServiceTest {

    private static GroundedAnswerService newService() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        TokenCostMetrics metrics =
                new TokenCostMetrics(new SimpleMeterRegistry(), new CostProperties());
        return new GroundedAnswerService(builder, metrics);
    }

    @Test
    void sources_areDeduplicatedInFirstSeenOrder() {
        var docs =
                List.of(
                        Document.builder()
                                .text("a")
                                .metadata(Map.of("source", "a.pdf", "page", 1))
                                .build(),
                        Document.builder()
                                .text("a-again")
                                .metadata(Map.of("source", "a.pdf", "page", 1)) // duplicate citation
                                .build(),
                        Document.builder().text("b").metadata(Map.of("source", "b.pdf")).build());

        var sources = newService().sources(docs);

        assertThat(sources).containsExactly(new Source("a.pdf", 1), new Source("b.pdf", null));
    }

    @Test
    void isNoAnswer_detectsSentinel() {
        assertThat(GroundedAnswerService.isNoAnswer(GroundedAnswerService.NO_ANSWER)).isTrue();
        assertThat(GroundedAnswerService.isNoAnswer("Eine echte Antwort.")).isFalse();
        assertThat(GroundedAnswerService.isNoAnswer(null)).isFalse();
    }
}
