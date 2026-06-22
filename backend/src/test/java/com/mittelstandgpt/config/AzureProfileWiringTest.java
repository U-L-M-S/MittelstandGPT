package com.mittelstandgpt.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.azure.AzureVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Provider-portability guarantee (Workstream D): under the {@code azure} profile the
 * Azure OpenAI chat/embedding models and the Azure AI Search vector store are wired,
 * while the local Ollama/Qdrant backends are not — the exact mirror of
 * {@link LocalProfileWiringTest}. The same agentic/eval/observability code runs on
 * either backend; only configuration changes.
 *
 * <p>Dummy credentials are supplied so the Azure SDK clients construct without a
 * network call (they connect lazily on first use). A live run against real Azure is
 * documented separately and CI-skipped, like the repo's other credentialled paths.
 */
@SpringBootTest(
        properties = {
            // Dummy creds — valid-looking but never contacted (clients are lazy).
            "spring.ai.azure.openai.api-key=test-key",
            "spring.ai.azure.openai.endpoint=https://dummy.openai.azure.com",
            "spring.ai.azure.openai.chat.options.deployment-name=gpt-4o-mini",
            "spring.ai.azure.openai.embedding.options.deployment-name=text-embedding-3-small",
            "spring.ai.vectorstore.azure.url=https://dummy.search.windows.net",
            "spring.ai.vectorstore.azure.api-key=test-key"
        })
@ActiveProfiles("azure")
class AzureProfileWiringTest {

    @Autowired private ApplicationContext context;

    @Test
    void azureProvidersAreWired() {
        assertThat(context.getBeanNamesForType(AzureOpenAiChatModel.class))
                .as("Azure chat model wired under azure")
                .isNotEmpty();
        assertThat(context.getBeanNamesForType(AzureOpenAiEmbeddingModel.class))
                .as("Azure embedding model wired under azure")
                .isNotEmpty();
        assertThat(context.getBean(VectorStore.class))
                .as("azure vector store is Azure AI Search")
                .isInstanceOf(AzureVectorStore.class);
    }

    @Test
    void localBackendsAreNotInstantiatedUnderAzure() {
        assertThat(context.getBeanNamesForType(OllamaChatModel.class))
                .as("Ollama must be excluded under azure")
                .isEmpty();
    }
}
