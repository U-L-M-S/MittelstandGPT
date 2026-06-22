package com.mittelstandgpt.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * M0 wiring guarantee: under the default {@code local} profile the application
 * context boots fully offline (Ollama with {@code pull-model-strategy=never} and
 * Qdrant with {@code initialize-schema=false} make no network calls at startup),
 * the local providers are wired, and the Azure backends — present on the
 * classpath — are NOT instantiated. This is the regression guard for the
 * provider-portability work: adding the Azure starters must never pull Azure into
 * the offline path or require Azure credentials to boot.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            // Keep startup fully offline: do not let the Qdrant store create its
            // collection (the only startup-time network call on this path).
            "spring.ai.vectorstore.qdrant.initialize-schema=false",
            // Point the best-effort registry rebuild at a port that refuses fast.
            "app.qdrant.rest-url=http://localhost:6"
        })
@ActiveProfiles("local")
class LocalProfileWiringTest {

    @Autowired private ApplicationContext context;

    @Test
    void localProvidersAreWired() {
        assertThat(context.getBeanNamesForType(OllamaChatModel.class))
                .as("Ollama chat model wired under local")
                .isNotEmpty();
        assertThat(context.getBeanNamesForType(OllamaEmbeddingModel.class))
                .as("Ollama embedding model wired under local")
                .isNotEmpty();
        assertThat(context.getBean(VectorStore.class))
                .as("local vector store is Qdrant")
                .isInstanceOf(QdrantVectorStore.class);
    }

    @Test
    void azureBackendsAreNotInstantiatedOffline() {
        assertThat(context.getBeanNamesForType(AzureOpenAiChatModel.class))
                .as("Azure chat model must be excluded under local")
                .isEmpty();
        assertThat(context.getBeanNamesForType(AzureOpenAiEmbeddingModel.class))
                .as("Azure embedding model must be excluded under local")
                .isEmpty();
    }
}
