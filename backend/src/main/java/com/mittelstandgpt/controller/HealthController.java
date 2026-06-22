package com.mittelstandgpt.controller;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Health endpoints.
 *
 * <ul>
 *   <li>{@code GET /api/health} — basic liveness (also used by the Docker healthcheck).
 *   <li>{@code GET /api/health/ai} — connectivity to Ollama and Qdrant.
 * </ul>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final RestClient ollamaClient;
    private final RestClient qdrantClient;

    public HealthController(
            @Value("${spring.ai.ollama.base-url:}") String ollamaBaseUrl,
            @Value("${app.qdrant.rest-url:}") String qdrantRestUrl) {
        this.ollamaClient = RestClient.builder().baseUrl(ollamaBaseUrl).requestFactory(timeouts()).build();
        this.qdrantClient = RestClient.builder().baseUrl(qdrantRestUrl).requestFactory(timeouts()).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "MittelstandGPT");
    }

    /** Verifies the backend can reach both infrastructure services. */
    @GetMapping("/ai")
    public ResponseEntity<Map<String, Object>> ai() {
        Map<String, Object> ollama = checkOllama();
        Map<String, Object> qdrant = checkQdrant();

        boolean up = "UP".equals(ollama.get("status")) && "UP".equals(qdrant.get("status"));
        Map<String, Object> body = Map.of(
                "status", up ? "UP" : "DOWN",
                "ollama", ollama,
                "qdrant", qdrant);
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> checkOllama() {
        try {
            Map<String, Object> tags = ollamaClient.get().uri("/api/tags").retrieve().body(Map.class);
            List<Map<String, Object>> models =
                    tags == null ? List.of() : (List<Map<String, Object>>) tags.getOrDefault("models", List.of());
            List<String> names = models.stream().map(m -> String.valueOf(m.get("name"))).toList();
            return Map.of("status", "UP", "models", names);
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", describe(e));
        }
    }

    private Map<String, Object> checkQdrant() {
        try {
            qdrantClient.get().uri("/healthz").retrieve().toBodilessEntity();
            return Map.of("status", "UP");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", describe(e));
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
