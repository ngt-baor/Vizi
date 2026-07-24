package com.example.vizi.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

class GeminiClientTests {

    @Test
    void generateTextPostsPromptAndReturnsFirstCandidateText() throws Exception {
        var path = new AtomicReference<String>();
        var query = new AtomicReference<String>();
        var requestBody = new AtomicReference<String>();
        var apiKeyHeader = new AtomicReference<String>();
        var server = startServer(path, query, requestBody, apiKeyHeader, 200, """
                {"candidates":[{"content":{"parts":[{"text":"pong"}]}}]}
                """);

        try {
            var service = new AiConfigService("test api key", "models/gemini-test", "image-model");
            var usageLogs = mock(AiUsageLogService.class);
            var client = new GeminiClient(
                    service,
                    usageLogs,
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            var text = client.generateText("Reply with pong.");

            assertThat(text).isEqualTo("pong");
            assertThat(path.get()).isEqualTo("/models/gemini-test:generateContent");
            assertThat(query.get()).isNull();
            assertThat(apiKeyHeader.get()).isEqualTo("test api key");
            assertThat(requestBody.get()).contains("\"contents\"");
            assertThat(requestBody.get()).contains("\"text\":\"Reply with pong.\"");
            verify(usageLogs).record(
                    eq("text.generate"),
                    eq("models/gemini-test"),
                    eq("SUCCESS"),
                    longThat(value -> value >= 0),
                    isNull()
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingGeminiKeyStopsBeforeHttpCall() {
        var service = new AiConfigService("", "gemini-test", "image-model");
        var usageLogs = mock(AiUsageLogService.class);
        var client = new GeminiClient(service, usageLogs, new ObjectMapper(), HttpClient.newHttpClient(), "http://127.0.0.1:1");

        assertThatThrownBy(() -> client.generateText("hello"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).isEqualTo("GEMINI_API_KEY is not configured");
                });
        verify(usageLogs).record(
                eq("text.generate"),
                eq("gemini-test"),
                eq("FAILED"),
                longThat(value -> value >= 0),
                eq("HTTP_503")
        );
    }

    @Test
    void emptyGeminiResponseReturnsBadGateway() throws Exception {
        var server = startServer(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), 200, """
                {"candidates":[{"content":{"parts":[]}}]}
                """);

        try {
            var service = new AiConfigService("test-api-key", "gemini-test", "image-model");
            var usageLogs = mock(AiUsageLogService.class);
            var client = new GeminiClient(
                    service,
                    usageLogs,
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> client.generateText("hello"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(exception.getReason()).isEqualTo("Gemini response has no text");
                    });
            verify(usageLogs).record(
                    eq("text.generate"),
                    eq("gemini-test"),
                    eq("FAILED"),
                    longThat(value -> value >= 0),
                    eq("HTTP_502")
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesServiceUnavailableTwiceThenReturnsSuccessfulResponse() throws Exception {
        var requestCount = new AtomicInteger();
        var server = startSequenceServer(requestCount, null, 503, 503, 200);

        try {
            var service = new AiConfigService("test-api-key", "gemini-test", "image-model");
            var usageLogs = mock(AiUsageLogService.class);
            var client = new GeminiClient(
                    service,
                    usageLogs,
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThat(client.generateText("hello")).isEqualTo("pong");
            assertThat(requestCount.get()).isEqualTo(3);
            verify(usageLogs).record(
                    eq("text.generate"),
                    eq("gemini-test"),
                    eq("SUCCESS"),
                    longThat(value -> value >= 0),
                    isNull()
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rateLimitIsRetriedTwiceThenMappedToTooManyRequests() throws Exception {
        var requestCount = new AtomicInteger();
        var server = startSequenceServer(requestCount, "0", 429, 429, 429);

        try {
            var service = new AiConfigService("test-api-key", "gemini-test", "image-model");
            var usageLogs = mock(AiUsageLogService.class);
            var client = new GeminiClient(
                    service,
                    usageLogs,
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> client.generateText("hello"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        assertThat(exception.getReason()).isEqualTo("Gemini rate limit exceeded");
                    });
            assertThat(requestCount.get()).isEqualTo(3);
            verify(usageLogs).record(
                    eq("text.generate"),
                    eq("gemini-test"),
                    eq("FAILED"),
                    longThat(value -> value >= 0),
                    eq("HTTP_429")
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void serviceUnavailableIsRetriedTwiceThenMappedToServiceUnavailable() throws Exception {
        var requestCount = new AtomicInteger();
        var server = startSequenceServer(requestCount, "0", 503, 503, 503);

        try {
            var service = new AiConfigService("test-api-key", "gemini-test", "image-model");
            var usageLogs = mock(AiUsageLogService.class);
            var client = new GeminiClient(
                    service,
                    usageLogs,
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> client.generateText("hello"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.getReason()).isEqualTo("Gemini service is unavailable");
                    });
            assertThat(requestCount.get()).isEqualTo(3);
            verify(usageLogs).record(
                    eq("text.generate"),
                    eq("gemini-test"),
                    eq("FAILED"),
                    longThat(value -> value >= 0),
                    eq("HTTP_503")
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    void otherUpstreamErrorsAreNotRetriedAndRemainBadGateway() throws Exception {
        var requestCount = new AtomicInteger();
        var server = startSequenceServer(requestCount, "0", 500, 200);

        try {
            var service = new AiConfigService("test-api-key", "gemini-test", "image-model");
            var usageLogs = mock(AiUsageLogService.class);
            var client = new GeminiClient(
                    service,
                    usageLogs,
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> client.generateText("hello"))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
            assertThat(requestCount.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retryAfterDelayIsBoundedToTwoSeconds() {
        assertThat(GeminiClient.retryAfterDelayMillis("0")).isZero();
        assertThat(GeminiClient.retryAfterDelayMillis("10")).isEqualTo(2_000);
        assertThat(GeminiClient.retryAfterDelayMillis("invalid")).isEqualTo(-1);
    }

    private HttpServer startSequenceServer(
            AtomicInteger requestCount,
            String retryAfter,
            int... statuses
    ) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var requestNumber = requestCount.incrementAndGet();
            var status = statuses[Math.min(requestNumber - 1, statuses.length - 1)];
            exchange.getRequestBody().readAllBytes();
            var responseBody = status == 200
                    ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"pong\"}]}}]}"
                    : "{}";
            var response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            if (retryAfter != null) {
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
            }
            exchange.sendResponseHeaders(status, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        return server;
    }

    private HttpServer startServer(
            AtomicReference<String> path,
            AtomicReference<String> query,
            AtomicReference<String> requestBody,
            AtomicReference<String> apiKeyHeader,
            int status,
            String responseBody
    ) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        return server;
    }
}
