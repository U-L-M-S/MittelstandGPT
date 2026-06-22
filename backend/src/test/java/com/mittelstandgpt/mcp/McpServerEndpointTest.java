package com.mittelstandgpt.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Boots the real embedded server and confirms the MCP SSE transport is actually
 * served: connecting to the SSE endpoint yields the initial handshake event that
 * carries the JSON-RPC message endpoint. Needs no external services (the MCP server
 * only depends on the in-process knowledge-base tools), so it runs offline.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.autoconfigure.exclude="
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration,"
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingAutoConfiguration,"
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiImageAutoConfiguration,"
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiAudioTranscriptionAutoConfiguration,"
                    + "org.springframework.ai.vectorstore.azure.autoconfigure.AzureVectorStoreAutoConfiguration,"
                    + "org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
            "spring.ai.mcp.server.enabled=true",
            "app.qdrant.rest-url="
        })
@ActiveProfiles("local")
@Import(McpServerEndpointTest.Store.class)
class McpServerEndpointTest {

    @TestConfiguration
    static class Store {
        @Bean
        VectorStore vectorStore(EmbeddingModel embeddingModel) {
            return SimpleVectorStore.builder(embeddingModel).build();
        }
    }

    @LocalServerPort private int port;

    @Test
    void mcpSseEndpointServesTheHandshake() {
        String firstEvent =
                WebClient.create("http://localhost:" + port)
                        .get()
                        .uri("/mcp/sse")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .retrieve()
                        .bodyToFlux(String.class)
                        .next()
                        .block(Duration.ofSeconds(15));

        // Receiving the initial SSE event proves the MCP server is live; its payload
        // is the message endpoint used for subsequent JSON-RPC (tools/list, etc.).
        assertThat(firstEvent).isNotNull();
        assertThat(firstEvent).contains("/mcp/message");
    }
}
