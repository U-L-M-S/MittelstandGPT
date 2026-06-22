package com.mittelstandgpt.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * RAG chat endpoints.
 *
 * <ul>
 *   <li>{@code POST /api/chat} — answer + sources as a single JSON response.
 *   <li>{@code POST /api/chat/stream} — answer streamed as Server-Sent Events
 *       ({@code token} events, then a {@code sources} event, then {@code done}).
 * </ul>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AgenticRagService rag;
    private final ObjectMapper objectMapper;

    public ChatController(AgenticRagService rag, ObjectMapper objectMapper) {
        this.rag = rag;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.question())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bitte eine Frage eingeben."));
        }
        return ResponseEntity.ok(rag.ask(request.question().strip()));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.question())) {
            return Flux.just(sse("error", "Bitte eine Frage eingeben."));
        }
        AgenticRagService.StreamResult result = rag.stream(request.question().strip());
        StringBuilder answer = new StringBuilder();
        Flux<ServerSentEvent<String>> tokens =
                result.answerTokens().doOnNext(answer::append).map(t -> sse("token", t));
        // Resolved after all tokens have streamed: omit sources for a "not found" answer.
        Flux<ServerSentEvent<String>> tail = Flux.defer(() -> {
            List<Source> sources =
                    GroundedAnswerService.isNoAnswer(answer.toString()) ? List.of() : result.sources();
            return Flux.just(sse("sources", writeSources(sources)), sse("done", "end"));
        });
        return Flux.concat(tokens, tail);
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder(data).event(event).build();
    }

    private String writeSources(List<Source> sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
