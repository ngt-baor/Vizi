package com.example.vizi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class AiPatchSchema {

    static final int VERSION = 1;

    private static final Pattern LAYER_ID = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]{0,79}$");
    private static final Pattern OPERATION = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "editStrength", "targetSide", "summary", "actions"
    );
    private static final Set<String> ACTION_FIELDS = Set.of(
            "op", "layerId", "text", "fill", "stroke", "strokeWidth", "fontFamily",
            "fontSize", "fontWeight", "lineHeight", "letterSpacing", "x", "y", "width",
            "height", "opacity", "rotation", "layer"
    );

    private final ObjectMapper objectMapper;

    AiPatchSchema(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    AiPatch parse(String json) {
        var root = readObject(json, "Patch");
        rejectUnknownFields(root, ROOT_FIELDS, "Patch");

        var version = requiredInteger(root, "schemaVersion");
        if (version != VERSION) {
            throw invalid("Unsupported schemaVersion");
        }
        var editStrength = requiredEnum(root, "editStrength", AiEditStrength.class);
        var targetSide = requiredEnum(root, "targetSide", AiTargetSide.class);
        var summary = requiredString(root, "summary", 500);
        var actionsNode = root.get("actions");
        if (actionsNode == null || !actionsNode.isArray() || actionsNode.isEmpty() || actionsNode.size() > 20) {
            throw invalid("Patch actions must contain 1 to 20 items");
        }

        var actions = new ArrayList<AiPatchAction>();
        for (int index = 0; index < actionsNode.size(); index++) {
            actions.add(parseAction(actionsNode.get(index), index));
        }
        return new AiPatch(
                version,
                editStrength,
                targetSide,
                summary,
                List.copyOf(actions)
        );
    }

    private AiPatchAction parseAction(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw invalid("Patch action " + index + " must be an object");
        }
        rejectUnknownFields(node, ACTION_FIELDS, "Patch action " + index);
        var op = requiredString(node, "op", 64);
        if (!OPERATION.matcher(op).matches()) {
            throw invalid("Patch action " + index + " has an invalid op");
        }
        var layerId = requiredString(node, "layerId", 80);
        if (!LAYER_ID.matcher(layerId).matches()) {
            throw invalid("Patch action " + index + " has an invalid layerId");
        }

        var action = new AiPatchAction(
                op,
                layerId,
                optionalString(node, "text", 1000),
                optionalString(node, "fill", 32),
                optionalString(node, "stroke", 32),
                optionalNumber(node, "strokeWidth"),
                optionalString(node, "fontFamily", 160),
                optionalInteger(node, "fontSize"),
                optionalInteger(node, "fontWeight"),
                optionalNumber(node, "lineHeight"),
                optionalNumber(node, "letterSpacing"),
                optionalNumber(node, "x"),
                optionalNumber(node, "y"),
                optionalNumber(node, "width"),
                optionalNumber(node, "height"),
                optionalNumber(node, "opacity"),
                optionalNumber(node, "rotation"),
                optionalObject(node, "layer")
        );
        if (!action.hasChange()) {
            throw invalid("Patch action " + index + " must include a change");
        }
        return action;
    }

    private JsonNode readObject(String json, String label) {
        if (json == null || json.isBlank()) {
            throw invalid(label + " JSON is required");
        }
        final JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (RuntimeException exception) {
            throw invalid(label + " JSON is invalid");
        }
        if (node == null || !node.isObject()) {
            throw invalid(label + " must be an object");
        }
        return node;
    }

    private static void rejectUnknownFields(JsonNode object, Set<String> allowed, String label) {
        for (String field : object.propertyNames()) {
            if (!allowed.contains(field)) {
                throw invalid(label + " contains an unsupported field");
            }
        }
    }

    private static String requiredString(JsonNode object, String field, int maxLength) {
        var value = optionalString(object, field, maxLength);
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static String optionalString(JsonNode object, String field, int maxLength) {
        var value = object.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isString()) {
            throw invalid(field + " must be a string");
        }
        var text = value.stringValue().trim();
        if (text.isEmpty() || text.length() > maxLength) {
            throw invalid(field + " is invalid");
        }
        return text;
    }

    private static Integer requiredInteger(JsonNode object, String field) {
        var value = optionalInteger(object, field);
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static Integer optionalInteger(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isIntegralNumber()) {
            throw invalid(field + " must be an integer");
        }
        return value.intValue();
    }

    private static Double optionalNumber(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) {
            throw invalid(field + " must be a finite number");
        }
        return value.doubleValue();
    }

    private static JsonNode optionalObject(JsonNode object, String field) {
        var value = object.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isObject()) {
            throw invalid(field + " must be an object");
        }
        return value;
    }

    private static <T extends Enum<T>> T requiredEnum(JsonNode object, String field, Class<T> type) {
        var value = requiredString(object, field, 40);
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " is invalid");
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
