package com.mittelstandgpt.chat;

import com.mittelstandgpt.document.DocumentIngestionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Retrieval-augmented generation: embed the question, retrieve the most similar
 * chunks from Qdrant, build a strictly grounded German prompt, and let the local
 * Ollama model answer. Returns the answer plus the source citations.
 */
@Service
public class RagService {

    private static final int TOP_K = 4;

    private static final String NO_ANSWER =
            "Diese Information ist in den vorliegenden Dokumenten nicht enthalten.";

    private static final String SYSTEM_PROMPT =
            """
            Du bist der Wissensassistent eines Unternehmens und beantwortest Fragen \
            ausschließlich auf Grundlage des bereitgestellten Kontexts.

            Regeln:
            - Verwende nur Informationen aus dem Kontext, niemals eigenes Vorwissen.
            - Steht die Antwort nicht im Kontext, antworte exakt: "%s"
            - Erfinde keine Fakten, Zahlen oder Quellen.
            - Antworte auf Deutsch, sachlich und in vollständigen Sätzen.
            """
                    .formatted(NO_ANSWER);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /** Answer a question synchronously. */
    public ChatResponse ask(String question) {
        List<Document> docs = retrieve(question);
        if (docs.isEmpty()) {
            return new ChatResponse(NO_ANSWER, List.of());
        }
        String answer = chatClient.prompt().user(userMessage(question, docs)).call().content();
        // Don't cite sources for a "not found" answer — it would be misleading.
        List<Source> sources = isNoAnswer(answer) ? List.of() : sources(docs);
        return new ChatResponse(answer, sources);
    }

    /** Whether an answer is (or contains) the canned "not in the documents" response. */
    public static boolean isNoAnswer(String answer) {
        return answer != null && answer.contains(NO_ANSWER);
    }

    /** Answer a question as a token stream, with the sources resolved up front. */
    public StreamResult stream(String question) {
        List<Document> docs = retrieve(question);
        if (docs.isEmpty()) {
            return new StreamResult(List.of(), Flux.just(NO_ANSWER));
        }
        Flux<String> tokens = chatClient.prompt().user(userMessage(question, docs)).stream().content();
        return new StreamResult(sources(docs), tokens);
    }

    private List<Document> retrieve(String question) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(TOP_K).build());
        return docs != null ? docs : List.of();
    }

    private static String userMessage(String question, List<Document> docs) {
        String context = docs.stream().map(RagService::formatChunk).collect(Collectors.joining("\n\n---\n\n"));
        return "Kontext:\n" + context + "\n\nFrage: " + question;
    }

    private static String formatChunk(Document doc) {
        String source = asString(doc.getMetadata().get(DocumentIngestionService.META_SOURCE));
        Integer page = asInteger(doc.getMetadata().get(DocumentIngestionService.META_PAGE));
        String label = source == null ? "" : "[Quelle: " + source + (page != null ? ", Seite " + page : "") + "]\n";
        return label + doc.getText();
    }

    private static List<Source> sources(List<Document> docs) {
        Set<Source> unique = new LinkedHashSet<>();
        for (Document doc : docs) {
            String source = asString(doc.getMetadata().get(DocumentIngestionService.META_SOURCE));
            if (source != null) {
                unique.add(new Source(source, asInteger(doc.getMetadata().get(DocumentIngestionService.META_PAGE))));
            }
        }
        return new ArrayList<>(unique);
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** Sources known immediately, plus the answer streamed token by token. */
    public record StreamResult(List<Source> sources, Flux<String> answerTokens) {}
}
