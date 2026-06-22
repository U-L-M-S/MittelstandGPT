package com.mittelstandgpt.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.mittelstandgpt.document.DocumentIngestionService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Live, end-to-end verification of the agentic pipeline against a real local LLM
 * + real embeddings (Ollama). The vector store is the in-memory
 * {@link SimpleVectorStore} backed by the same Ollama embedding model, so the
 * full retrieve → grade → answer loop runs with real model judgments while
 * staying self-contained (no Qdrant container needed). The agent depends only on
 * the {@link VectorStore} interface, so this exercises identical behaviour to the
 * Qdrant-backed production path.
 *
 * <p>Guarded by {@code MI_IT=true} so the default offline build skips it. Run with:
 * {@code MI_IT=true mvn -Dtest=AgenticRagIT test} (requires Ollama on
 * {@code localhost:11434} with qwen2.5:3b-instruct + nomic-embed-text).
 */
@SpringBootTest(
        properties = {
            // Keep only Ollama + the in-memory store active (the test property
            // replaces the profile's exclude list, so the full set is restated).
            "spring.autoconfigure.exclude="
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiChatAutoConfiguration,"
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiEmbeddingAutoConfiguration,"
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiImageAutoConfiguration,"
                    + "org.springframework.ai.model.azure.openai.autoconfigure.AzureOpenAiAudioTranscriptionAutoConfiguration,"
                    + "org.springframework.ai.vectorstore.azure.autoconfigure.AzureVectorStoreAutoConfiguration,"
                    + "org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration",
            "mi.rag.grading-enabled=true",
            // top-k=2 reliably isolates the two relevant docs in this 3-doc fixture
            // corpus (the off-topic canteen doc ranks third), so citation precision
            // does not hinge on the small model's grading.
            "mi.rag.top-k=2",
            "mi.rag.max-hops=3"
        })
@ActiveProfiles("local")
@Import(AgenticRagIT.Fixtures.class)
@EnabledIfEnvironmentVariable(named = "MI_IT", matches = "true")
class AgenticRagIT {

    /** A self-contained corpus whose facts span multiple documents. */
    @TestConfiguration
    static class Fixtures {
        @Bean
        VectorStore vectorStore(EmbeddingModel embeddingModel) {
            SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
            store.add(
                    List.of(
                            chunk(
                                    "Der gesetzliche Urlaubsanspruch aller Mitarbeitenden beträgt 30"
                                        + " Arbeitstage pro Kalenderjahr.",
                                    "urlaub.pdf",
                                    1),
                            chunk(
                                    "Urlaub wird ausschließlich über das HR-Portal beantragt und muss"
                                        + " von der direkten Führungskraft genehmigt werden.",
                                    "antrag.pdf",
                                    1),
                            chunk(
                                    "Die Mitarbeiterkantine serviert warme Gerichte werktags von 11:30"
                                        + " bis 14:00 Uhr.",
                                    "kantine.pdf",
                                    1)));
            return store;
        }

        private static Document chunk(String text, String source, int page) {
            return Document.builder()
                    .text(text)
                    .metadata(
                            Map.of(
                                    DocumentIngestionService.META_SOURCE, source,
                                    DocumentIngestionService.META_PAGE, page))
                    .build();
        }
    }

    @Autowired private AgenticRagService rag;

    @Test
    void combinesTwoDocumentsAndCitesBoth() {
        ChatResponse response =
                rag.ask("Wie viele Urlaubstage stehen mir zu und wie beantrage ich meinen Urlaub?");

        assertThat(GroundedAnswerService.isNoAnswer(response.answer())).isFalse();
        assertThat(response.answer()).contains("30");
        assertThat(response.sources())
                .extracting(Source::source)
                .contains("urlaub.pdf", "antrag.pdf");
    }

    @Test
    void abstainsWhenAnswerNotInCorpus() {
        ChatResponse response =
                rag.ask("Wie hoch war die Dividende des Unternehmens im Jahr 2026?");

        assertThat(GroundedAnswerService.isNoAnswer(response.answer())).isTrue();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void retrievalLoopRespectsHopBound() {
        AgenticRagService.RetrievalOutcome outcome =
                rag.retrieveCorrectively("Wie viele Urlaubstage und wie beantragen?");

        assertThat(outcome.hops()).isBetween(1, 3);
        assertThat(outcome.chunks()).isNotEmpty();
    }

    @Test
    void streamingProducesGroundedTokensAndSources() {
        AgenticRagService.StreamResult result =
                rag.stream("Wie viele Urlaubstage stehen mir zu und wie beantrage ich meinen Urlaub?");

        String answer =
                result.answerTokens().collectList().block().stream().collect(Collectors.joining());

        assertThat(GroundedAnswerService.isNoAnswer(answer)).isFalse();
        assertThat(answer).contains("30");
        assertThat(result.sources()).extracting(Source::source).contains("urlaub.pdf");
    }
}
