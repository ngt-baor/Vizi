package com.example.vizi.ai;

import java.util.List;

import tools.jackson.databind.JsonNode;

record AiPatch(
        int schemaVersion,
        AiEditStrength editStrength,
        AiTargetSide targetSide,
        String summary,
        List<AiPatchAction> actions
) {
}

enum AiEditStrength {
    LIGHT,
    BALANCED,
    CREATIVE,
    DIRECT_COMMAND
}

enum AiTargetSide {
    FRONT,
    BACK
}

record AiPatchAction(
        String op,
        String layerId,
        String text,
        String fill,
        String stroke,
        Double strokeWidth,
        String fontFamily,
        Integer fontSize,
        Integer fontWeight,
        Double lineHeight,
        Double letterSpacing,
        Double x,
        Double y,
        Double width,
        Double height,
        Double opacity,
        Double rotation,
        JsonNode layer
) {

    boolean hasChange() {
        return "remove_layer".equals(op) || layer != null
                || text != null || fill != null || stroke != null || strokeWidth != null
                || fontFamily != null || fontSize != null || fontWeight != null
                || lineHeight != null || letterSpacing != null || x != null || y != null
                || width != null || height != null || opacity != null || rotation != null;
    }
}
