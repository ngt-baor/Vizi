package com.example.vizi.upload;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class BackgroundRemovalClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String sharedToken;
    private final String model;
    private final Duration timeout;

    @Autowired
    BackgroundRemovalClient(
            @Value("${app.background-removal.base-url:http://127.0.0.1:7000}") String baseUrl,
            @Value("${app.background-removal.shared-token:}") String sharedToken,
            @Value("${app.background-removal.model:u2netp}") String model,
            @Value("${app.background-removal.timeout-seconds:90}") long timeoutSeconds
    ) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), baseUrl, sharedToken, model, timeoutSeconds);
    }

    BackgroundRemovalClient(HttpClient httpClient, String baseUrl, String sharedToken, String model, long timeoutSeconds) {
        this.httpClient = httpClient;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.sharedToken = sharedToken == null ? "" : sharedToken.strip();
        this.model = model == null || model.isBlank() ? "u2netp" : model.strip();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    byte[] remove(byte[] input, String fileName, String contentType) {
        if (input == null || input.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }

        var boundary = "----ViziBackground" + UUID.randomUUID();
        var requestBuilder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/remove"))
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!sharedToken.isBlank()) {
            requestBuilder.header("X-Vizi-Internal-Token", sharedToken);
        }
        var request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, input, fileName, contentType, model)))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Background removal service failed");
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Background removal was interrupted", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Background removal service is unavailable", exception);
        }
    }


    private static byte[] multipartBody(String boundary, byte[] input, String fileName, String contentType, String model) {
        var output = new ByteArrayOutputStream(input.length + 256);
        write(output, "--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeFileName(fileName) + "\"\r\n");
        write(output, "Content-Type: " + (contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType) + "\r\n\r\n");
        output.writeBytes(input);
        write(output, "\r\n--" + boundary + "\r\n");
        write(output, "Content-Disposition: form-data; name=\"model\"\r\n\r\n");
        write(output, model + "\r\n");
        write(output, "--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:7000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
