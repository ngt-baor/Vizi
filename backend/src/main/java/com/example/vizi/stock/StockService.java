package com.example.vizi.stock;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

@Service
class StockService {

    private static final String DEFAULT_OPENVERSE_BASE_URL = "https://api.openverse.org/v1/images/";
    private static final String LICENSE_FILTER = "cc0,pdm,by";
    private static final int MAX_QUERY_LENGTH = 80;
    private static final int MAX_PAGE = 500;
    private static final int MAX_PAGE_SIZE = 48;
    private static final int MAX_CACHE_ENTRIES = 100;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGE_CACHE_ENTRIES = 200;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration IMAGE_CACHE_TTL = Duration.ofHours(1);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String openverseBaseUrl;
    private final ConcurrentMap<String, CachedPage> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedImage> imageCache = new ConcurrentHashMap<>();

    @Autowired
    StockService(
            ObjectMapper objectMapper,
            @Value("${app.stock.openverse-base-url:" + DEFAULT_OPENVERSE_BASE_URL + "}") String openverseBaseUrl
    ) {
        this(
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build(),
                openverseBaseUrl
        );
    }

    StockService(ObjectMapper objectMapper, HttpClient httpClient, String openverseBaseUrl) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.openverseBaseUrl = openverseBaseUrl == null || openverseBaseUrl.isBlank()
                ? DEFAULT_OPENVERSE_BASE_URL
                : openverseBaseUrl.trim();
    }

    StockSearchResponse search(String query, String kind, int page, int pageSize) {
        var cleanQuery = normalizeQuery(query);
        var cleanKind = normalizeKind(kind);
        var cleanPage = Math.max(1, Math.min(page, MAX_PAGE));
        var cleanPageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        var remoteQuery = cleanQuery.isBlank() ? "business card" : cleanQuery;
        var cacheKey = remoteQuery + "|" + cleanKind + "|" + cleanPage + "|" + cleanPageSize;
        var cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.response();
        }

        try {
            var request = HttpRequest.newBuilder(endpoint(remoteQuery, cleanPage, cleanPageSize))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Vizi/1.0 stock search")
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Stock provider failed with status " + response.statusCode()
                );
            }

            var result = parseResponse(response.body(), cleanKind, cleanPage, cleanPageSize);
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
            cache.put(cacheKey, new CachedPage(Instant.now().plus(CACHE_TTL), result));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock provider request was interrupted", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock provider request failed", exception);
        }
    }

    StockImage image(String id) {
        var cleanId = normalizeImageId(id);
        var cached = imageCache.get(cleanId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.image();
        }

        try {
            var request = HttpRequest.newBuilder(thumbnailEndpoint(cleanId))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                    .header("User-Agent", "Vizi/1.0 stock thumbnail")
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Stock image provider failed with status " + response.statusCode()
                );
            }

            var contentType = response.headers()
                    .firstValue("content-type")
                    .map(StockService::normalizeContentType)
                    .orElse("");
            var content = response.body();
            if (!supportedImageType(contentType) || content.length == 0 || content.length > MAX_IMAGE_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock image response is invalid");
            }

            var image = new StockImage(content, contentType);
            if (imageCache.size() >= MAX_IMAGE_CACHE_ENTRIES) {
                imageCache.clear();
            }
            imageCache.put(cleanId, new CachedImage(Instant.now().plus(IMAGE_CACHE_TTL), image));
            return image;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock image request was interrupted", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock image request failed", exception);
        }
    }

    private URI thumbnailEndpoint(String id) {
        var base = openverseBaseUrl.endsWith("/") ? openverseBaseUrl : openverseBaseUrl + "/";
        return URI.create(base + id + "/thumb/");
    }

    private static String normalizeImageId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stock image id");
        }
        return id;
    }

    private static String normalizeContentType(String value) {
        var separator = value.indexOf(';');
        return (separator >= 0 ? value.substring(0, separator) : value).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean supportedImageType(String contentType) {
        return contentType.equals("image/jpeg")
                || contentType.equals("image/png")
                || contentType.equals("image/webp")
                || contentType.equals("image/gif");
    }

    private URI endpoint(String query, int page, int pageSize) {
        var separator = openverseBaseUrl.contains("?") ? "&" : "?";
        var encodedLicense = URLEncoder.encode(LICENSE_FILTER, StandardCharsets.UTF_8);
        return URI.create(openverseBaseUrl + separator
                + "q=" + encode(query)
                + "&page=" + page
                + "&page_size=" + pageSize
                + "&license=" + encodedLicense);
    }

    private StockSearchResponse parseResponse(String body, String kind, int page, int pageSize) throws IOException {
        var root = objectMapper.readTree(body);
        var results = root.path("results");
        if (!results.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stock provider response has no results");
        }

        var assets = new ArrayList<StockAsset>();
        for (JsonNode item : (ArrayNode) results) {
            var id = stringValue(item, "id");
            var title = stringValue(item, "title");
            var previewUrl = id.matches("[A-Za-z0-9-]{1,100}") ? previewUrl(id) : "";
            if (id.isBlank() || title.isBlank() || previewUrl.isBlank()) {
                continue;
            }

            var creator = stringValue(item, "creator");
            assets.add(new StockAsset(
                    "openverse-" + id,
                    title,
                    kind.isBlank() ? "photo" : kind,
                    "Openverse",
                    previewUrl,
                    httpsUrl(stringValue(item, "foreign_landing_url")),
                    creator,
                    stringValue(item, "license"),
                    stringValue(item, "license_version"),
                    List.of("openverse", kind.isBlank() ? "photo" : kind),
                    creator.isBlank() ? "Openverse" : creator + " via Openverse"
            ));
        }

        var total = intValue(root, "result_count");
        return new StockSearchResponse(
                page,
                pageSize,
                total,
                page * pageSize < total,
                "openverse",
                "",
                List.copyOf(assets)
        );
    }

    private static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        var cleanQuery = query.trim();
        if (cleanQuery.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock search query is too long");
        }
        return cleanQuery;
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank() || "all".equalsIgnoreCase(kind)) {
            return "";
        }
        var cleanKind = kind.trim().toLowerCase();
        if (!cleanKind.matches("photo|illustration|background")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported stock asset kind");
        }
        return cleanKind;
    }

    private static String previewUrl(String id) {
        return "/api/stock/images/" + id;
    }
    private static String httpsUrl(String value) {
        try {
            var uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null ? value : "";
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String stringValue(JsonNode object, String field) {
        var value = object.path(field);
        return value.isString() ? value.stringValue() : "";
    }

    private static int intValue(JsonNode object, String field) {
        var value = object.path(field);
        return value.isNumber() ? value.intValue() : 0;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record CachedPage(Instant expiresAt, StockSearchResponse response) {
    }

    private record CachedImage(Instant expiresAt, StockImage image) {
    }
}