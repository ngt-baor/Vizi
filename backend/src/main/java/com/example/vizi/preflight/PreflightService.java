package com.example.vizi.preflight;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PreflightService {

    private static final double SAFE_ZONE_MM = 3;
    private static final double MINIMUM_IMAGE_DPI = 150;

    private final ObjectMapper objectMapper;

    public PreflightService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PreflightReport check(String canvasJson, double widthMm, double heightMm) {
        try {
            var canvas = objectMapper.readTree(canvasJson);
            var layers = canvas.get("layers");
            if (!canvas.isObject() || layers == null || !layers.isArray()) {
                return report(new PreflightIssue(
                        "ERROR",
                        "INVALID_CANVAS",
                        "Canvas JSON must contain a layers array.",
                        null
                ));
            }
            if (layers.isEmpty()) {
                return report(new PreflightIssue(
                        "ERROR",
                        "EMPTY_CANVAS",
                        "Canvas must contain at least one layer.",
                        null
                ));
            }
            var issues = new ArrayList<PreflightIssue>();
            addSafeZoneIssues(layers, widthMm, heightMm, issues);
            addImageResolutionIssues(layers, widthMm, heightMm, issues);
            return new PreflightReport(
                    issues.stream().noneMatch(issue -> issue.level().equals("ERROR")),
                    List.copyOf(issues)
            );
        } catch (Exception exception) {
            return report(new PreflightIssue(
                    "ERROR",
                    "INVALID_CANVAS",
                    "Canvas JSON is invalid.",
                    null
            ));
        }
    }

    private static PreflightReport report(PreflightIssue issue) {
        return new PreflightReport(false, List.of(issue));
    }

    private static void addSafeZoneIssues(
            JsonNode layers,
            double widthMm,
            double heightMm,
            List<PreflightIssue> issues
    ) {
        if (widthMm <= 0 || heightMm <= 0) {
            issues.add(new PreflightIssue(
                    "ERROR",
                    "INVALID_CANVAS_SIZE",
                    "Canvas width and height must be positive.",
                    null
            ));
            return;
        }

        var safeX = SAFE_ZONE_MM / widthMm * 100;
        var safeY = SAFE_ZONE_MM / heightMm * 100;
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (!layer.isObject()) {
                continue;
            }
            var type = text(layer, "type", "");
            var x = number(layer, "x", 8);
            var y = number(layer, "y", 8);
            var width = number(layer, "width", type.equals("text") ? 45 : 32);
            var height = number(layer, "height", type.equals("text") ? 16 : 26);
            if (isFullCanvasBackground(type, x, y, width, height)) {
                continue;
            }
            if (x < safeX || y < safeY || x + width > 100 - safeX || y + height > 100 - safeY) {
                var critical = type.equals("text") || type.equals("qr");
                issues.add(new PreflightIssue(
                        critical ? "ERROR" : "WARNING",
                        "LAYER_OUTSIDE_SAFE_ZONE",
                        critical
                                ? "Text and QR layers must stay inside the 3 mm safe zone."
                                : "Layer extends outside the 3 mm safe zone.",
                        index
                ));
            }
        }
    }

    private static boolean isFullCanvasBackground(
            String type,
            double x,
            double y,
            double width,
            double height
    ) {
        return (type.equals("rect") || type.equals("shape"))
                && x <= 0
                && y <= 0
                && x + width >= 100
                && y + height >= 100;
    }

    private static void addImageResolutionIssues(
            JsonNode layers,
            double widthMm,
            double heightMm,
            List<PreflightIssue> issues
    ) {
        if (widthMm <= 0 || heightMm <= 0) {
            return;
        }

        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (!layer.isObject() || !text(layer, "type", "").equals("image")) {
                continue;
            }
            var pixelWidth = number(layer, "pixelWidth", 0);
            var pixelHeight = number(layer, "pixelHeight", 0);
            var layerWidth = number(layer, "width", 32);
            var layerHeight = number(layer, "height", 26);
            if (pixelWidth <= 0 || pixelHeight <= 0 || layerWidth <= 0 || layerHeight <= 0) {
                continue;
            }

            var printWidthInches = widthMm * layerWidth / 100 / 25.4;
            var printHeightInches = heightMm * layerHeight / 100 / 25.4;
            var effectiveDpi = Math.min(
                    pixelWidth / printWidthInches,
                    pixelHeight / printHeightInches
            );
            if (effectiveDpi < MINIMUM_IMAGE_DPI) {
                issues.add(new PreflightIssue(
                        "WARNING",
                        "LOW_IMAGE_RESOLUTION",
                        "Image resolution is below 150 DPI at the current print size.",
                        index
                ));
            }
        }
    }

    private static double number(JsonNode node, String field, double fallback) {
        var value = node.get(field);
        return value != null && value.isNumber() ? value.doubleValue() : fallback;
    }

    private static String text(JsonNode node, String field, String fallback) {
        var value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : fallback;
    }
}
