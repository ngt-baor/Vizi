package com.example.vizi.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class AiPatchValidator {

    private static final Pattern LAYER_ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,79}$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Set<String> OPERATIONS = Set.of(
            "update_text", "update_fill", "update_stroke", "update_font",
            "update_geometry", "add_layer", "remove_layer"
    );
    private static final Set<String> NEW_LAYER_FIELDS = Set.of(
            "type", "name", "text", "fill", "stroke", "strokeWidth", "fontFamily",
            "fontSize", "fontWeight", "lineHeight", "letterSpacing", "x", "y", "width",
            "height", "opacity", "rotation"
    );
    private static final Set<String> NEW_LAYER_TYPES = Set.of("text", "rect", "shape");

    private final ObjectMapper objectMapper;

    AiPatchValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiPatch validate(AiPatch patch, String canvasJson, boolean allowsLayerMutation) {
        if (patch == null) {
            throw invalid("Patch is required");
        }
        var layers = layersById(canvasJson);
        for (var action : patch.actions()) {
            validateAction(action, layers, allowsLayerMutation);
        }
        return patch;
    }
    AiTextLayer textLayer(String canvasJson, String layerId) {
        var layer = requireLayer(layersById(canvasJson), layerId);
        requireType(layer, "text", "Text rewrite");
        var text = optionalString(layer, "text");
        if (text == null) {
            text = optionalString(layer, "value");
        }
        if (text == null || text.length() > 1_000) {
            throw invalid("Text layer must contain at most 1000 characters");
        }
        return new AiTextLayer(layerId, text);
    }

    private void validateAction(
            AiPatchAction action,
            Map<String, JsonNode> layers,
            boolean allowsLayerMutation
    ) {
        if (!OPERATIONS.contains(action.op())) {
            throw invalid("Patch action uses an unsupported operation");
        }

        switch (action.op()) {
            case "update_text" -> validateTextUpdate(action, requireLayer(layers, action.layerId()));
            case "update_fill" -> validateFillUpdate(action, requireLayer(layers, action.layerId()));
            case "update_stroke" -> validateStrokeUpdate(action, requireLayer(layers, action.layerId()));
            case "update_font" -> validateFontUpdate(action, requireLayer(layers, action.layerId()));
            case "update_geometry" -> validateGeometryUpdate(action, requireLayer(layers, action.layerId()));
            case "add_layer" -> validateAddLayer(action, layers, allowsLayerMutation);
            case "remove_layer" -> validateRemoveLayer(action, layers, allowsLayerMutation);
            default -> throw invalid("Patch action uses an unsupported operation");
        }
    }

    private static void validateTextUpdate(AiPatchAction action, JsonNode layer) {
        requireType(layer, "text", "update_text");
        rejectUnexpected(action, action.text() == null || action.fill() != null || action.stroke() != null
                || action.strokeWidth() != null || hasFontChange(action) || hasGeometryChange(action)
                || action.layer() != null);
    }

    private static void validateFillUpdate(AiPatchAction action, JsonNode layer) {
        rejectUnexpected(action, action.fill() == null || action.text() != null || action.stroke() != null
                || action.strokeWidth() != null || hasFontChange(action) || hasGeometryChange(action)
                || action.layer() != null);
        requireColor(action.fill(), "fill");
    }

    private static void validateStrokeUpdate(AiPatchAction action, JsonNode layer) {
        rejectUnexpected(action, action.text() != null || action.fill() != null || hasFontChange(action)
                || hasGeometryChange(action) || action.layer() != null
                || (action.stroke() == null && action.strokeWidth() == null));
        if (action.stroke() != null) {
            requireColor(action.stroke(), "stroke");
        }
        if (action.strokeWidth() != null) {
            requireRange(action.strokeWidth(), 0, 20, "strokeWidth");
        }
    }

    private static void validateFontUpdate(AiPatchAction action, JsonNode layer) {
        requireType(layer, "text", "update_font");
        rejectUnexpected(action, action.text() != null || action.fill() != null || action.stroke() != null
                || action.strokeWidth() != null || !hasFontChange(action) || hasGeometryChange(action)
                || action.layer() != null);
        validateFontValues(action);
    }

    private static void validateGeometryUpdate(AiPatchAction action, JsonNode layer) {
        rejectUnexpected(action, action.text() != null || action.fill() != null || action.stroke() != null
                || action.strokeWidth() != null || hasFontChange(action) || !hasGeometryChange(action)
                || action.layer() != null);
        var type = stringValue(layer, "type", "shape");
        var x = action.x() != null ? action.x() : numberValue(layer, "x", 0);
        var y = action.y() != null ? action.y() : numberValue(layer, "y", 0);
        var width = action.width() != null ? action.width() : numberValue(layer, "width", type.equals("text") ? 45 : 32);
        var height = action.height() != null ? action.height() : numberValue(layer, "height", type.equals("text") ? 16 : 26);
        requireCardBounds(x, y, width, height);
        if (action.opacity() != null) {
            requireRange(action.opacity(), 0, 1, "opacity");
        }
        if (action.rotation() != null) {
            requireRange(action.rotation(), 0, 360, "rotation");
        }
    }

    private static void validateAddLayer(
            AiPatchAction action,
            Map<String, JsonNode> layers,
            boolean allowsLayerMutation
    ) {
        requireLayerMutation(allowsLayerMutation);
        if (layers.containsKey(action.layerId())) {
            throw invalid("New layerId already exists");
        }
        rejectUnexpected(action, action.layer() == null || action.text() != null || action.fill() != null
                || action.stroke() != null || action.strokeWidth() != null || hasFontChange(action)
                || hasGeometryChange(action));
        validateNewLayer(action.layer());
    }

    private static void validateRemoveLayer(
            AiPatchAction action,
            Map<String, JsonNode> layers,
            boolean allowsLayerMutation
    ) {
        requireLayerMutation(allowsLayerMutation);
        requireLayer(layers, action.layerId());
        rejectUnexpected(action, action.text() != null || action.fill() != null || action.stroke() != null
                || action.strokeWidth() != null || hasFontChange(action) || hasGeometryChange(action)
                || action.layer() != null);
    }

    private static void validateNewLayer(JsonNode layer) {
        for (String field : layer.propertyNames()) {
            if (!NEW_LAYER_FIELDS.contains(field)) {
                throw invalid("New layer contains an unsupported field");
            }
        }
        var type = requiredString(layer, "type", 20);
        if (!NEW_LAYER_TYPES.contains(type)) {
            throw invalid("New layer type is not allowed");
        }
        var x = requiredNumber(layer, "x");
        var y = requiredNumber(layer, "y");
        var width = requiredNumber(layer, "width");
        var height = requiredNumber(layer, "height");
        requireCardBounds(x, y, width, height);

        validateOptionalString(layer, "name", 160);
        validateOptionalString(layer, "text", 1_000);
        validateOptionalString(layer, "fontFamily", 160);
        if (type.equals("text") && optionalString(layer, "text") == null) {
            throw invalid("New text layer requires text");
        }
        validateOptionalColor(layer, "fill");
        validateOptionalColor(layer, "stroke");
        validateOptionalRange(layer, "strokeWidth", 0, 20);
        validateOptionalIntegerRange(layer, "fontSize", 6, 120);
        validateOptionalIntegerRange(layer, "fontWeight", 100, 900);
        validateOptionalRange(layer, "lineHeight", 0.5, 3);
        validateOptionalRange(layer, "letterSpacing", -10, 30);
        validateOptionalRange(layer, "opacity", 0, 1);
        validateOptionalRange(layer, "rotation", 0, 360);
    }

    private Map<String, JsonNode> layersById(String canvasJson) {
        final JsonNode canvas;
        try {
            canvas = objectMapper.readTree(canvasJson);
        } catch (RuntimeException exception) {
            throw invalid("Canvas JSON is invalid");
        }
        var layers = canvas == null ? null : canvas.get("layers");
        if (canvas == null || !canvas.isObject() || layers == null || !layers.isArray()) {
            throw invalid("Canvas must contain a layers array");
        }

        var result = new LinkedHashMap<String, JsonNode>();
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            if (layer == null || !layer.isObject()) {
                throw invalid("Canvas layer " + index + " must be an object");
            }
            var layerId = legacyOrStableId(layer, index);
            if (result.putIfAbsent(layerId, layer) != null) {
                throw invalid("Canvas layer IDs must be unique");
            }
        }
        return result;
    }

    private static String legacyOrStableId(JsonNode layer, int index) {
        var id = optionalString(layer, "id");
        return id != null && LAYER_ID.matcher(id).matches() ? id : "layer-" + (index + 1);
    }

    private static JsonNode requireLayer(Map<String, JsonNode> layers, String layerId) {
        var layer = layers.get(layerId);
        if (layer == null) {
            throw invalid("Patch action targets a layer that does not exist");
        }
        return layer;
    }

    private static void requireLayerMutation(boolean allowsLayerMutation) {
        if (!allowsLayerMutation) {
            throw invalid("Adding or removing layers requires an explicit user command");
        }
    }

    private static boolean hasFontChange(AiPatchAction action) {
        return action.fontFamily() != null || action.fontSize() != null || action.fontWeight() != null
                || action.lineHeight() != null || action.letterSpacing() != null;
    }

    private static boolean hasGeometryChange(AiPatchAction action) {
        return action.x() != null || action.y() != null || action.width() != null || action.height() != null
                || action.opacity() != null || action.rotation() != null;
    }

    private static void validateFontValues(AiPatchAction action) {
        if (action.fontFamily() != null && action.fontFamily().isBlank()) {
            throw invalid("fontFamily is invalid");
        }
        if (action.fontSize() != null && (action.fontSize() < 6 || action.fontSize() > 120)) {
            throw invalid("fontSize is out of range");
        }
        if (action.fontWeight() != null && (action.fontWeight() < 100 || action.fontWeight() > 900)) {
            throw invalid("fontWeight is out of range");
        }
        if (action.lineHeight() != null) {
            requireRange(action.lineHeight(), 0.5, 3, "lineHeight");
        }
        if (action.letterSpacing() != null) {
            requireRange(action.letterSpacing(), -10, 30, "letterSpacing");
        }
    }

    private static void requireType(JsonNode layer, String expected, String operation) {
        if (!expected.equals(stringValue(layer, "type", ""))) {
            throw invalid(operation + " requires a " + expected + " layer");
        }
    }

    private static void requireCardBounds(double x, double y, double width, double height) {
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || x + width > 100 || y + height > 100) {
            throw invalid("Layer geometry must stay within the card");
        }
    }

    private static void requireColor(String value, String field) {
        if (!COLOR.matcher(value).matches()) {
            throw invalid(field + " must be a six-digit hex color");
        }
    }

    private static void requireRange(double value, double minimum, double maximum, String field) {
        if (value < minimum || value > maximum) {
            throw invalid(field + " is out of range");
        }
    }

    private static void rejectUnexpected(AiPatchAction action, boolean invalid) {
        if (invalid) {
            throw invalid("Patch action contains fields that do not match its operation");
        }
    }

    private static String requiredString(JsonNode object, String field, int maximumLength) {
        var value = optionalString(object, field);
        if (value == null || value.length() > maximumLength) {
            throw invalid(field + " is invalid");
        }
        return value;
    }

    private static String optionalString(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw invalid(field + " must be a string");
        }
        var text = value.stringValue().trim();
        return text.isEmpty() ? null : text;
    }

    private static double requiredNumber(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null || !value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw invalid(field + " must be a finite number");
        }
        return value.doubleValue();
    }

    private static double numberValue(JsonNode object, String field, double fallback) {
        var value = object.get(field);
        return value != null && value.isNumber() && Double.isFinite(value.doubleValue())
                ? value.doubleValue()
                : fallback;
    }

    private static String stringValue(JsonNode object, String field, String fallback) {
        var value = object.get(field);
        return value != null && value.isString() ? value.stringValue() : fallback;
    }

    private static void validateOptionalString(JsonNode object, String field, int maximumLength) {
        var value = optionalString(object, field);
        if (value != null && value.length() > maximumLength) {
            throw invalid(field + " is invalid");
        }
    }

    private static void validateOptionalColor(JsonNode object, String field) {
        var value = optionalString(object, field);
        if (value != null) {
            requireColor(value, field);
        }
    }

    private static void validateOptionalRange(JsonNode object, String field, double minimum, double maximum) {
        var value = object.get(field);
        if (value != null) {
            if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
                throw invalid(field + " must be a finite number");
            }
            requireRange(value.doubleValue(), minimum, maximum, field);
        }
    }

    private static void validateOptionalIntegerRange(JsonNode object, String field, int minimum, int maximum) {
        var value = object.get(field);
        if (value != null) {
            if (!value.isIntegralNumber() || value.intValue() < minimum || value.intValue() > maximum) {
                throw invalid(field + " is out of range");
            }
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

record AiTextLayer(String layerId, String text) {
}