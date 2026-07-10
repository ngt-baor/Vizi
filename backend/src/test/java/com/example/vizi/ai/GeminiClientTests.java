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
        var server = startServer(path, query, requestBody, 200, """
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
            assertThat(query.get()).isEqualTo("key=test+api+key");
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
        var server = startServer(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), 200, """
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

    private HttpServer startServer(
            AtomicReference<String> path,
            AtomicReference<String> query,
            AtomicReference<String> requestBody,
            int status,
            String responseBody
    ) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
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
