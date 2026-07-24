package com.example.vizi.ai;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ThreadLocalRandom;
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
    private static final int MAX_ATTEMPTS = 3;
    private static final long MAX_RETRY_DELAY_MILLIS = 2_000;
    private static final long[] RETRY_BACKOFF_MILLIS = {250, 750};

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
        return generateText("text.generate", prompt);
    }

    String generateText(String feature, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt is required");
        }
        if (feature == null || feature.isBlank() || feature.length() > 80) {
            throw new IllegalArgumentException("AI feature is invalid");
        }

        var model = aiConfigService.textModel();
        var started = System.nanoTime();
        try {
            var apiKey = aiConfigService.requireGeminiApiKey();
            var request = HttpRequest.newBuilder(endpoint(model))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt), StandardCharsets.UTF_8))
                    .build();

            try {
                var response = sendWithRetry(request);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw upstreamFailure(response.statusCode());
                }
                var text = extractText(response.body());
                aiUsageLogService.record(feature, model, "SUCCESS", elapsedMillis(started), null);
                return text;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request was interrupted", exception);
            } catch (IOException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini request failed", exception);
            }
        } catch (ResponseStatusException exception) {
            aiUsageLogService.record(
                    feature,
                    model,
                    "FAILED",
                    elapsedMillis(started),
                    "HTTP_" + exception.getStatusCode().value()
            );
            throw exception;
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        for (var attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            var response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (!isRetryable(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                return response;
            }
            Thread.sleep(retryDelayMillis(response, attempt - 1));
        }
        throw new IllegalStateException("Gemini retry loop completed without a response");
    }

    private static boolean isRetryable(int status) {
        return status == HttpStatus.TOO_MANY_REQUESTS.value()
                || status == HttpStatus.SERVICE_UNAVAILABLE.value();
    }

    private static ResponseStatusException upstreamFailure(int status) {
        return switch (status) {
            case 429 -> new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Gemini rate limit exceeded"
            );
            case 503 -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini service is unavailable"
            );
            default -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Gemini request failed with status " + status
            );
        };
    }

    private static long retryDelayMillis(HttpResponse<?> response, int retryIndex) {
        var retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            var requestedDelay = retryAfterDelayMillis(retryAfter.get());
            if (requestedDelay >= 0) {
                return requestedDelay;
            }
        }

        var baseDelay = RETRY_BACKOFF_MILLIS[retryIndex];
        var jitter = ThreadLocalRandom.current().nextLong((baseDelay / 4) + 1);
        return Math.min(MAX_RETRY_DELAY_MILLIS, baseDelay + jitter);
    }

    static long retryAfterDelayMillis(String value) {
        var normalized = value == null ? "" : value.strip();
        try {
            var seconds = Long.parseLong(normalized);
            return seconds < 0 ? -1 : Math.min(seconds, 2) * 1_000;
        } catch (NumberFormatException ignored) {
            try {
                var retryAt = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                var delay = Duration.between(Instant.now(), retryAt).toMillis();
                return Math.min(MAX_RETRY_DELAY_MILLIS, Math.max(0, delay));
            } catch (DateTimeParseException invalidDate) {
                return -1;
            }
        }
    }

    private static long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private URI endpoint(String model) {
        var normalizedModel = model == null ? "" : model.strip();
        if (normalizedModel.startsWith("models/")) {
            normalizedModel = normalizedModel.substring("models/".length());
        }
        if (normalizedModel.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Gemini text model is not configured");
        }
        var encodedModel = URLEncoder.encode(normalizedModel, StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/models/" + encodedModel + ":generateContent");
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
