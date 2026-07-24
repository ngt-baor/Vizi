package com.example.vizi.icon;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Service
class   Icons8Service {

    private static final String CREDIT_TEXT = "Icons by Icons8";
    private static final String CREDIT_URL = "https://icons8.com";
    private static final int MAX_QUERY_LENGTH = 80;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String searchBaseUrl;

    @Autowired
    Icons8Service(
            ObjectMapper objectMapper,
            @Value("${app.icons8.api-key:}") String apiKey,
            @Value("${app.icons8.search-base-url:https://search.icons8.com/api/iconsets/v5/search}") String searchBaseUrl
    ) {
        this(objectMapper, HttpClient.newHttpClient(), apiKey, searchBaseUrl);
    }

    Icons8Service(ObjectMapper objectMapper, HttpClient httpClient, String apiKey, String searchBaseUrl) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.searchBaseUrl = searchBaseUrl == null || searchBaseUrl.isBlank()
                ? "https://search.icons8.com/api/iconsets/v5/search"
                : searchBaseUrl.trim();
    }

    Icons8SearchResponse search(String term, String language, String platform, int amount) {
        return search(term, language, platform, amount, 0);
    }

    Icons8SearchResponse search(String term, String language, String platform, int amount, int offset) {
        var cleanTerm = validateTerm(term);
        var cleanLanguage = normalizeSimpleToken(language, "en");
        var cleanPlatform = normalizePlatformList(platform);
        var cleanAmount = Math.max(1, Math.min(amount, 48));
        var cleanOffset = Math.max(0, Math.min(offset, 10_000));

        if (apiKey.isBlank()) {
            return Icons8SearchResponse.disabled("Icons8 search is not configured. Set ICONS8_API_KEY on the backend.");
        }

        try {
            var request = HttpRequest.newBuilder(endpoint(cleanTerm, cleanLanguage, cleanPlatform, cleanAmount, cleanOffset))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Vizi/1.0")
                    .header("Api-Key", apiKey)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Icons8 request failed with status " + response.statusCode());
            }
            return parseResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Icons8 request was interrupted", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Icons8 request failed", exception);
        }
    }

    private URI endpoint(String term, String language, String platform, int amount, int offset) {
        var query = new StringBuilder()
                .append("term=").append(encode(term))
                .append("&amount=").append(amount)
                .append("&language=").append(encode(language))
                .append("&offset=").append(offset);
        if (!platform.isBlank()) {
            query.append("&platform=").append(encode(platform));
        }
        return URI.create(searchBaseUrl + (searchBaseUrl.contains("?") ? "&" : "?") + query);
    }

    private Icons8SearchResponse parseResponse(String body) throws IOException {
        var root = objectMapper.readTree(body);
        var icons = root.path("icons");
        if (!icons.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Icons8 response has no icons");
        }
        var result = new ArrayList<Icons8Icon>();
        for (JsonNode icon : (ArrayNode) icons) {
            var id = stringValue(icon, "id");
            var name = stringValue(icon, "name");
            if (id.isBlank() || name.isBlank()) {
                continue;
            }
            var commonName = stringValue(icon, "commonName");
            var platform = stringValue(icon, "platform");
            var upstreamPreviewUrl = stringValue(icon, "previewUrl");
            result.add(new Icons8Icon(
                    id,
                    name,
                    stringValue(icon, "category"),
                    stringValue(icon, "subcategory"),
                    commonName,
                    platform,
                    trustedPreviewUrl(upstreamPreviewUrl) ? upstreamPreviewUrl : previewUrl(id, commonName, platform),
                    sourceUrl(id, commonName.isBlank() ? name : commonName),
                    true,
                    booleanValue(icon, "isColor"),
                    booleanValue(icon, "isAnimated")
            ));
        }
        return new Icons8SearchResponse(
                true,
                true,
                CREDIT_TEXT,
                CREDIT_URL,
                stringValue(root, "message"),
                List.copyOf(result)
        );
    }

    private static String validateTerm(String term) {
        if (term == null || term.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icon search term is required");
        }
        var cleanTerm = term.trim();
        if (cleanTerm.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Icon search term is too long");
        }
        return cleanTerm;
    }

    private static String normalizeSimpleToken(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        var clean = value.trim();
        return clean.matches("[A-Za-z0-9_-]{1,32}") ? clean : fallback;
    }

    private static String normalizePlatformList(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var platforms = value.trim().split(",");
        var clean = new ArrayList<String>();
        for (var platform : platforms) {
            var token = platform.trim();
            if (!token.matches("[A-Za-z0-9_-]{1,32}")) {
                return "";
            }
            clean.add(token);
        }
        return String.join(",", clean);
    }

    private static String previewUrl(String id, String commonName, String platform) {
        var style = switch (platform) {
            case "androidL" -> "material";
            case "fluent" -> "fluency";
            default -> platform;
        };
        var slug = commonName.matches("[A-Za-z0-9_-]{1,120}") ? commonName : id;
        if (!style.matches("[A-Za-z0-9_-]{1,32}")) {
            return "https://img.icons8.com/?size=96&id=" + encode(id) + "&format=png";
        }
        return "https://img.icons8.com/" + encode(style) + "/96/" + encode(slug) + ".png";
    }

    private static boolean trustedPreviewUrl(String value) {
        try {
            var uri = URI.create(value);
            var host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && ("icons8.com".equalsIgnoreCase(host) || host.toLowerCase().endsWith(".icons8.com"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String sourceUrl(String id, String name) {
        if (!name.isBlank()) {
            return "https://icons8.com/icon/" + encode(id) + "/" + encode(name.toLowerCase().replace(' ', '-'));
        }
        return "https://icons8.com/icon/" + encode(id);
    }

    private static String stringValue(JsonNode object, String field) {
        var value = object.path(field);
        return value.isString() ? value.stringValue() : "";
    }

    private static boolean booleanValue(JsonNode object, String field) {
        var value = object.path(field);
        return value.isBoolean() && value.booleanValue();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
