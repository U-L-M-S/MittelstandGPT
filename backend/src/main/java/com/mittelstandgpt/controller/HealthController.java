package com.mittelstandgpt.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic liveness endpoint for the frontend and quick manual checks.
 *
 * <p>Connectivity checks against Ollama and Qdrant are added in Phase 1 under
 * {@code /api/health/ai}.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "MittelstandGPT");
    }
}
