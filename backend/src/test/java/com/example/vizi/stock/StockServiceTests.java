package com.example.vizi.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class StockServiceTests {

    @Test
    void searchNormalizesOpenverseResultsAndUsesOpenLicenseFilter() throws Exception {
        var path = new AtomicReference<String>();
        var query = new AtomicReference<String>();
        var server = startServer(path, query, 200, """
                {
                  "result_count": 49,
                  "results": [
                    {
                      "id": "asset-1",
                      "title": "Mountain card",
                      "thumbnail": "https://api.openverse.org/v1/images/asset-1/thumb/",
                      "foreign_landing_url": "https://example.test/source/asset-1",
                      "creator": "Example creator",
                      "license": "by",
                      "license_version": "4.0"
                    },
                    {
                      "id": "asset-2",
                      "title": "Fallback thumbnail",
                      "thumbnail": "https://evil.example/asset-2.png",
                      "foreign_landing_url": "javascript:alert(1)",
                      "creator": "",
                      "license": "cc0",
                      "license_version": ""
                    }
                  ]
                }
                """);

        try {
            var service = new StockService(
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/images/"
            );

            var response = service.search("mountain card", "photo", 2, 12);

            assertThat(path.get()).isEqualTo("/v1/images/");
            assertThat(query.get()).contains(
                    "q=mountain+card",
                    "page=2",
                    "page_size=12",
                    "license=cc0%2Cpdm%2Cby"
            );
            assertThat(response.page()).isEqualTo(2);
            assertThat(response.pageSize()).isEqualTo(12);
            assertThat(response.total()).isEqualTo(49);
            assertThat(response.hasMore()).isTrue();
            assertThat(response.assets()).hasSize(2);
            assertThat(response.assets().get(0).previewUrl()).isEqualTo("/api/stock/images/asset-1");
            assertThat(response.assets().get(1).previewUrl()).isEqualTo("/api/stock/images/asset-2");
            assertThat(response.assets().get(1).sourceUrl()).isBlank();
            assertThat(response.assets().get(0).credit()).contains("Example creator");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void blankQueryUsesBusinessCardBrowseTerm() throws Exception {
        var query = new AtomicReference<String>();
        var server = startServer(new AtomicReference<>(), query, 200, "{\"result_count\":0,\"results\":[]}");
        try {
            var service = new StockService(
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/images"
            );

            service.search(" ", "all", 1, 24);

            assertThat(query.get()).contains("q=business+card", "page=1", "page_size=24");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void imageUsesValidatedThumbnailEndpointAndCaches() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var body = new byte[] { 1, 2, 3, 4 };
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/images/asset-1/thumb/");
            exchange.getResponseHeaders().set("Content-Type", "image/webp; charset=binary");
            exchange.sendResponseHeaders(200, body.length);
            try (var response = exchange.getResponseBody()) {
                response.write(body);
            }
        });
        server.start();

        try {
            var service = new StockService(
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/images"
            );

            var first = service.image("asset-1");
            var second = service.image("asset-1");

            assertThat(first.contentType()).isEqualTo("image/webp");
            assertThat(first.content()).containsExactly(body);
            assertThat(second.content()).containsExactly(body);
            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidKindReturnsBadRequest() {
        var service = new StockService(
                new ObjectMapper(),
                HttpClient.newHttpClient(),
                "http://127.0.0.1:1/images"
        );

        assertThatThrownBy(() -> service.search("tree", "video", 1, 24))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upstreamFailureReturnsBadGateway() throws Exception {
        var server = startServer(new AtomicReference<>(), new AtomicReference<>(), 503, "{}");
        try {
            var service = new StockService(
                    new ObjectMapper(),
                    HttpClient.newHttpClient(),
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/images"
            );

            assertThatThrownBy(() -> service.search("tree", "photo", 1, 24))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(
            AtomicReference<String> path,
            AtomicReference<String> query,
            int status,
            String responseBody
    ) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
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