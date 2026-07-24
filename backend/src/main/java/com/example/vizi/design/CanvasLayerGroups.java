package com.example.vizi.design;

import java.util.List;

import tools.jackson.databind.JsonNode;

public final class CanvasLayerGroups {

    private CanvasLayerGroups() {
    }

    public static List<LayerGroup> from(JsonNode canvas) {
        if (canvas == null || !canvas.isObject()) {
            return List.of();
        }

        // Preserve legacy compatibility: root layers take precedence when both schemas are present.
        var layers = canvas.get("layers");
        if (layers != null && layers.isArray()) {
            return List.of(new LayerGroup(null, layers));
        }

        var pages = canvas.get("pages");
        if (pages == null || !pages.isObject()) {
            return List.of();
        }
        var front = pages.get("front");
        var back = pages.get("back");
        var frontLayers = front != null && front.isObject() ? front.get("layers") : null;
        var backLayers = back != null && back.isObject() ? back.get("layers") : null;
        if (frontLayers == null || !frontLayers.isArray()
                || backLayers == null || !backLayers.isArray()) {
            return List.of();
        }
        return List.of(
                new LayerGroup("front", frontLayers),
                new LayerGroup("back", backLayers)
        );
    }

    public static List<LayerGroup> require(JsonNode canvas) {
        var groups = from(canvas);
        if (!groups.isEmpty()) {
            return groups;
        }

        var pages = canvas != null && canvas.isObject() ? canvas.get("pages") : null;
        if (pages != null && pages.isObject()) {
            if (pages.get("front") == null) {
                throw new IllegalArgumentException("Canvas V2 is missing the front page");
            }
            if (pages.get("back") == null) {
                throw new IllegalArgumentException("Canvas V2 is missing the back page");
            }
        }
        throw new IllegalArgumentException("Canvas must contain a layers array or V2 front/back pages");
    }

    public record LayerGroup(String side, JsonNode layers) {
    }
}
