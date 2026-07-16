package com.example.vizi.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BackgroundRemovalClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsImageModelAndSharedTokenToRemovalService() throws Exception {
        var output = pngBytes();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/remove", exchange -> {
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            var valid = "shared-test-token".equals(exchange.getRequestHeaders().getFirst("X-Vizi-Internal-Token"))
                    && body.contains("name=\"file\"")
                    && body.contains("filename=\"portrait_name.png\"")
                    && body.contains("name=\"model\"")
                    && body.contains("u2netp");
            if (!valid) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
            exchange.close();
        });
        server.start();

        var client = new BackgroundRemovalClient(
                HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "shared-test-token",
                "u2netp",
                5
        );

        assertThat(client.remove(pngBytes(), "portrait name.png", "image/png")).isEqualTo(output);
    }

    @Test
    void mapsRemoteFailureToBadGateway() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/remove", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        var client = new BackgroundRemovalClient(
                HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "",
                "u2netp",
                5
        );

        assertThatThrownBy(() -> client.remove(pngBytes(), "portrait.png", "image/png"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void mapsUnavailableServiceToServiceUnavailable() {
        var client = new BackgroundRemovalClient(
                HttpClient.newHttpClient(),
                "http://127.0.0.1:1",
                "",
                "u2netp",
                1
        );

        assertThatThrownBy(() -> client.remove(pngBytes(), "portrait.png", "image/png"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
    }
}
