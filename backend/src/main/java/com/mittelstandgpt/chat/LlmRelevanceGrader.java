package com.mittelstandgpt.chat;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * LLM-as-judge implementation of {@link RelevanceGrader} that drives the
 * Corrective-RAG loop's <em>sufficiency</em> decision.
 *
 * <p>Design note: with a small local model, asking the LLM to <em>drop</em>
 * individual chunks proved too noisy — it would discard clearly-relevant
 * passages and produce missing citations. So relevance/recall is handled by the
 * deterministic vector similarity search (and the optional
 * {@code mi.rag.similarity-threshold}); this grader keeps the retrieved chunks
 * and instead judges whether they are sufficient to fully answer the question,
 * proposing a reformulated follow-up query when they are not. Abstention is
 * ultimately guaranteed by the strict grounding prompt in
 * {@link GroundedAnswerService}, which returns the sentinel when the context does
 * not support an answer. Fails open: any error keeps the chunks and stops the
 * loop, so a flaky judge can never drop context or stall.
 */
@Component
public class LlmRelevanceGrader implements RelevanceGrader {

    private static final Logger log = LoggerFactory.getLogger(LlmRelevanceGrader.class);

    private static final String SUFFICIENCY_SYSTEM =
            """
            Du prüfst, ob die vorliegenden Auszüge ausreichen, um die Frage VOLLSTÄNDIG zu \
            beantworten. Antworte mit dem einzelnen Wort "AUSREICHEND", wenn alle Teile der \
            Frage aus den Auszügen beantwortet werden können. Andernfalls antworte mit einer \
            kurzen, präzisen deutschen Suchanfrage nach der noch fehlenden Information — nur \
            die Suchanfrage, ohne Erklärung.
            """;

    private static final String SUFFICIENT_MARKER = "AUSREICHEND";

    private final ChatClient chatClient;

    public LlmRelevanceGrader(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public GradeResult grade(String question, List<Document> retrieved) {
        if (retrieved.isEmpty()) {
            // Nothing came back; signal "not sufficient" and let the loop reformulate.
            return new GradeResult(List.of(), false, question);
        }
        try {
            Sufficiency suff = assessSufficiency(question, retrieved);
            log.info(
                    "Grading: {} chunk(s) kept, sufficient={}, followUp='{}'",
                    retrieved.size(),
                    suff.sufficient(),
                    suff.followUpQuery());
            return new GradeResult(List.copyOf(retrieved), suff.sufficient(), suff.followUpQuery());
        } catch (Exception e) {
            log.warn("Sufficiency grading failed ({}); keeping all retrieved chunks", e.toString());
            return GradeResult.keepAll(retrieved);
        }
    }

    private Sufficiency assessSufficiency(String question, List<Document> chunks) {
        StringBuilder ctx = new StringBuilder();
        for (Document d : chunks) {
            ctx.append("- ").append(d.getText()).append('\n');
        }
        String response =
                chatClient
                        .prompt()
                        .system(SUFFICIENCY_SYSTEM)
                        .user("Frage: " + question + "\n\nVorliegende Auszüge:\n" + ctx)
                        .call()
                        .content();
        if (response == null || response.toUpperCase(Locale.ROOT).contains(SUFFICIENT_MARKER)) {
            return new Sufficiency(true, "");
        }
        return new Sufficiency(false, response.strip());
    }

    private record Sufficiency(boolean sufficient, String followUpQuery) {}
}
