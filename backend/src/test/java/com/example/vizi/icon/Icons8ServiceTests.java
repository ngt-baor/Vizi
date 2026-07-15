package com.example.vizi.icon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class Icons8ServiceTests {

    @Test
    void searchNormalizesIcons8ResultsAndSendsApiKeyHeader() throws Exception {
        var path = new AtomicReference<String>();
        var query = new AtomicReference<String>();
        var apiKey = new AtomicReference<String>();
        var server = startServer(path, query, apiKey, 200, """
                {
                  "success": true,
                  "icons": [
                    {
                      "id": "9659",
                      "name": "Phone",
                      "commonName": "phone",
                      "category": "Mobile",
                      "subcategory": "Calls",
                      "platform": "ios7",
                      "isColor": false,
                      "isAnimated": false
                    }
                  ],
                  "message": "ok"
                }
                """);

        try {
            var service = new Icons8Service(
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "test-icons-key",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/iconsets/v5/search"
            );

            var response = service.search("phone", "en", "ios7", 12);

            assertThat(path.get()).isEqualTo("/api/iconsets/v5/search");
            assertThat(query.get()).contains("term=phone", "language=en", "platform=ios7", "amount=12");
            assertThat(apiKey.get()).isEqualTo("test-icons-key");
            assertThat(response.configured()).isTrue();
            assertThat(response.creditRequired()).isTrue();
            assertThat(response.creditText()).isEqualTo("Icons by Icons8");
            assertThat(response.creditUrl()).isEqualTo("https://icons8.com");
            assertThat(response.icons()).hasSize(1);
            assertThat(response.icons().get(0).previewUrl()).contains("img.icons8.com", "id=9659");
            assertThat(response.icons().get(0).sourceUrl()).contains("icons8.com/icon/9659");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blankSearchTermReturnsBadRequest() {
        var service = new Icons8Service(new ObjectMapper(), HttpClient.newHttpClient(), "", "http://127.0.0.1:1/search");

        assertThatThrownBy(() -> service.search(" ", "en", "", 10))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upstreamFailureReturnsBadGateway() throws Exception {
        var server = startServer(new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), 503, "{}");
        try {
            var service = new Icons8Service(
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/search"
            );

            assertThatThrownBy(() -> service.search("phone", "en", "", 10))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(
            AtomicReference<String> path,
            AtomicReference<String> query,
            AtomicReference<String> apiKey,
            int status,
            String responseBody
    ) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            apiKey.set(exchange.getRequestHeaders().getFirst("Api-Key"));
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
