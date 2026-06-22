package com.mittelstandgpt.chat;

import com.mittelstandgpt.document.DocumentIngestionService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * The generation half of RAG: produce a strictly grounded answer from a fixed
 * set of context chunks. Owns the grounding contract — the German system prompt,
 * the no-answer sentinel, and source citation — unchanged from the original
 * pipeline so the strict-grounding guarantee and SSE behaviour are preserved.
 */
@Service
public class GroundedAnswerService {

    /** Returned verbatim when the documents do not support an answer. */
    public static final String NO_ANSWER =
            "Diese Information ist in den vorliegenden Dokumenten nicht enthalten.";

    // The distinctive part of the sentinel, used for tolerant detection: small
    // models occasionally paraphrase the leading article ("Die" vs "Diese") or
    // punctuation, which must still count as an abstention so sources are omitted.
    private static final String NO_ANSWER_MARKER =
            "information ist in den vorliegenden dokumenten nicht enthalten";

    private static final String SYSTEM_PROMPT =
            """
            Du bist der Wissensassistent eines Unternehmens. Beantworte die Frage des \
            Nutzers ausschließlich auf Grundlage des bereitgestellten Kontexts. Der \
            Kontext besteht aus mehreren Auszügen.

            Vorgehen:
            - Prüfe JEDEN Auszug einzeln; die benötigte Information kann in einem \
              beliebigen Auszug stehen.
            - Trage die passenden Fakten und Zahlen aus allen relevanten Auszügen \
              zusammen und formuliere eine klare, vollständige Antwort.
            - Beantworte nur die konkret gestellte Frage. Behandelt der Kontext lediglich \
              ein verwandtes, aber anderes Thema, gilt die Information als nicht vorhanden.
            - Nur wenn KEIN Auszug eine zur Frage passende Information enthält, antworte \
              exakt mit diesem Satz: "%s"
            - Verwende niemals eigenes Vorwissen und erfinde keine Fakten, Zahlen oder Quellen.
            - Verweise im Antworttext nicht auf Dokumentnamen oder Auszüge; die \
              Quellenangaben werden separat angezeigt.
            - Antworte auf Deutsch, sachlich und in vollständigen Sätzen.
            """
                    .formatted(NO_ANSWER);

    private final ChatClient chatClient;

    public GroundedAnswerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
    }

    /** Grounded answer, synchronously. */
    public String answer(String question, List<Document> chunks) {
        return chatClient.prompt().user(userMessage(question, chunks)).call().content();
    }

    /** Grounded answer streamed token by token. */
    public Flux<String> streamAnswer(String question, List<Document> chunks) {
        return chatClient.prompt().user(userMessage(question, chunks)).stream().content();
    }

    /** Whether an answer is (or contains) the canned "not in the documents" response. */
    public static boolean isNoAnswer(String answer) {
        return answer != null
                && answer.toLowerCase(java.util.Locale.ROOT).contains(NO_ANSWER_MARKER);
    }

    /** De-duplicated source citations for the given chunks, in first-seen order. */
    public List<Source> sources(List<Document> docs) {
        Set<Source> unique = new LinkedHashSet<>();
        for (Document doc : docs) {
            String source = asString(doc.getMetadata().get(DocumentIngestionService.META_SOURCE));
            if (source != null) {
                unique.add(
                        new Source(
                                source,
                                asInteger(doc.getMetadata().get(DocumentIngestionService.META_PAGE))));
            }
        }
        return new ArrayList<>(unique);
    }

    private static String userMessage(String question, List<Document> docs) {
        String context =
                docs.stream()
                        .map(GroundedAnswerService::formatChunk)
                        .collect(Collectors.joining("\n\n---\n\n"));
        return "Kontext:\n" + context + "\n\nFrage: " + question;
    }

    private static String formatChunk(Document doc) {
        String source = asString(doc.getMetadata().get(DocumentIngestionService.META_SOURCE));
        Integer page = asInteger(doc.getMetadata().get(DocumentIngestionService.META_PAGE));
        String label =
                source == null
                        ? ""
                        : "[Quelle: " + source + (page != null ? ", Seite " + page : "") + "]\n";
        return label + doc.getText();
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
