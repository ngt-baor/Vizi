package com.example.vizi.ai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Service
class GeminiClient {

    private final AiConfigService aiConfigService;
    private final AiUsageLogService aiUsageLogService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;

    @Autowired
    GeminiClient(
            AiConfigService aiConfigService,
            AiUsageLogService aiUsageLogService,
            ObjectMapper objectMapper,
            @Value("${app.ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl
    ) {
        this(aiConfigService, aiUsageLogService, objectMapper, HttpClient.newHttpClient(), baseUrl);
    }

    GeminiClient(
            AiConfigService aiConfigService,
            AiUsageLogService aiUsageLogService,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            String baseUrl
    ) {
        this.aiConfigService = aiConfigService;
        this.aiUsageLogService = aiUsageLogService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    String generateText(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is required");
        }

        var model = aiConfigService.textModel();
        var started = System.nanoTime();
        try {
            var apiKey = aiConfigService.requireGeminiApiKey();
            var request = HttpRequest.newBuilder(endpoint(model, apiKey))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt), StandardCharsets.UTF_8))
                    .build();

            try {
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed with status " + response.statusCode());
                }
                var text = extractText(response.body());
                aiUsageLogService.record("text.generate", model, "SUCCESS", elapsedMillis(started), null);
                return text;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request was interrupted", exception);
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed", exception);
            }
        } catch (ResponseStatusException exception) {
            aiUsageLogService.record(
                    "text.generate",
                    model,
                    "FAILED",
                    elapsedMillis(started),
                    "HTTP_" + exception.getStatusCode().value()
            );
            throw exception;
        }
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private URI endpoint(String model, String apiKey) {
        var normalizedModel = model == null ? "" : model.strip();
        if (normalizedModel.startsWith("models/")) {
            normalizedModel = normalizedModel.substring("models/".length());
        }
        if (normalizedModel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini text model is not configured");
        }
        var encodedModel = URLEncoder.encode(normalizedModel, StandardCharsets.UTF_8);
        var encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/models/" + encodedModel + ":generateContent?key=" + encodedKey);
    }

    private String requestBody(String prompt) {
        var root = objectMapper.createObjectNode();
        var content = objectMapper.createObjectNode();
        var part = objectMapper.createObjectNode().put("text", prompt);
        content.set("parts", objectMapper.createArrayNode().add(part));
        root.set("contents", objectMapper.createArrayNode().add(content));
        return root.toString();
    }

    private String extractText(String body) throws IOException {
        var root = objectMapper.readTree(body);
        var candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response has no candidates");
        }

        var parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response has no text");
        }

        var text = new StringBuilder();
        for (JsonNode part : (ArrayNode) parts) {
            var value = part.path("text");
            if (value.isString()) {
                var partText = value.stringValue();
                if (!partText.isBlank()) {
                    text.append(partText);
                }
            }
        }
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini response has no text");
        }
        return text.toString();
    }
}
