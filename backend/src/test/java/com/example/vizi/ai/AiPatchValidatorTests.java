package com.example.vizi.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class AiPatchValidatorTests {

    private final AiPatchSchema schema = new AiPatchSchema(new ObjectMapper());
    private final AiPatchValidator validator = new AiPatchValidator(new ObjectMapper());

    @Test
    void acceptsTextUpdateForLegacyLayerId() {
        var patch = parse("""
                {"schemaVersion":1,"editStrength":"light","targetSide":"front","summary":"Rewrite headline",
                 "actions":[{"op":"update_text","layerId":"layer-1","text":"Vizi Atelier"}]}
                """);

        assertThat(validator.validate(patch, canvas("""
                {"layers":[{"type":"text","text":"Old headline","x":10,"y":10,"width":40,"height":12}]}
                """), false)).isSameAs(patch);
    }

    @Test
    void acceptsTextUpdateForV2FrontLayer() {
        var patch = parse("""
                {"schemaVersion":1,"editStrength":"light","targetSide":"front","summary":"Rewrite headline",
                 "actions":[{"op":"update_text","layerId":"layer-1","text":"Vizi Atelier"}]}
                """);

        var v2Canvas = """
                {"schemaVersion":2,"pages":{"front":{"id":"front","layers":[{"id":"layer-1","type":"text","content":"Old headline"}]},"back":{"id":"back","layers":[]}}}
                """;

        assertThat(validator.validate(patch, v2Canvas, false)).isSameAs(patch);
    }

    @Test
    void rejectsV2CanvasMissingBackPageWithSpecificMessage() {
        var v2Canvas = """
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"id":"front","layers":[]}
                  }
                }
                """;

        assertThatThrownBy(() -> validator.textLayer(v2Canvas, "title", AiTargetSide.FRONT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Canvas V2 is missing the back page");
    }

    @Test
    void reportsLayerNotFoundWhenRequestedV2SideIsEmpty() {
        var v2Canvas = """
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"id":"front","layers":[]},
                    "back": {"id":"back","layers":[{"id":"title","type":"text","content":"Back title"}]}
                  }
                }
                """;

        assertThatThrownBy(() -> validator.textLayer(v2Canvas, "title", AiTargetSide.FRONT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patch action targets a layer that does not exist");
    }

    @Test
    void resolvesV2TextLayerWithinRequestedSideWhenIdsOverlap() {
        var v2Canvas = """
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"id":"front","layers":[{"id":"shared-title","type":"text","content":"Front title"}]},
                    "back": {"id":"back","layers":[{"id":"shared-title","type":"text","content":"Back title"}]}
                  }
                }
                """;

        assertThat(validator.textLayer(v2Canvas, "shared-title", AiTargetSide.FRONT).text())
                .isEqualTo("Front title");
        assertThat(validator.textLayer(v2Canvas, "shared-title", AiTargetSide.BACK).text())
                .isEqualTo("Back title");
    }

    @Test
    void rejectsDuplicateLayerIdsWithinTheSameV2Side() {
        var v2Canvas = """
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"id":"front","layers":[
                      {"id":"title","type":"text","content":"First"},
                      {"id":"title","type":"text","content":"Second"}
                    ]},
                    "back": {"id":"back","layers":[]}
                  }
                }
                """;

        assertThatThrownBy(() -> validator.textLayer(v2Canvas, "title", AiTargetSide.FRONT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Canvas layer IDs must be unique");
    }

    @Test
    void rejectsUnknownOperationAndMissingTarget() {
        var unknownOperation = parse("""
                {"schemaVersion":1,"editStrength":"light","targetSide":"front","summary":"Test",
                 "actions":[{"op":"future_operation","layerId":"layer-1","text":"New"}]}
                """);
        assertThatThrownBy(() -> validator.validate(unknownOperation, canvas("""
                {"layers":[{"type":"text"}]}
                """), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patch action uses an unsupported operation");

        var missingTarget = parse("""
                {"schemaVersion":1,"editStrength":"light","targetSide":"front","summary":"Test",
                 "actions":[{"op":"update_text","layerId":"layer-2","text":"New"}]}
                """);
        assertThatThrownBy(() -> validator.validate(missingTarget, canvas("""
                {"layers":[{"type":"text"}]}
                """), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patch action targets a layer that does not exist");
    }

    @Test
    void rejectsMutatingLayerStructureWithoutExplicitPermission() {
        var patch = parse("""
                {"schemaVersion":1,"editStrength":"direct_command","targetSide":"front","summary":"Add text",
                 "actions":[{"op":"add_layer","layerId":"tagline","layer":{"type":"text","text":"Luxury print studio","x":10,"y":70,"width":40,"height":10}}]}
                """);

        assertThatThrownBy(() -> validator.validate(patch, canvas("""
                {"layers":[{"id":"title","type":"text"}]}
                """), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Adding or removing layers requires an explicit user command");
    }

    @Test
    void acceptsSafeNewLayerOnlyWhenExplicitlyPermitted() {
        var patch = parse("""
                {"schemaVersion":1,"editStrength":"direct_command","targetSide":"front","summary":"Add text",
                 "actions":[{"op":"add_layer","layerId":"tagline","layer":{"type":"text","text":"Luxury print studio","fill":"#1F2937","x":10,"y":70,"width":40,"height":10}}]}
                """);

        assertThat(validator.validate(patch, canvas("""
                {"layers":[{"id":"title","type":"text"}]}
                """), true)).isSameAs(patch);
    }

    @Test
    void rejectsNewLayerOutsideCardOrWithUnsafeFields() {
        var outsideCard = parse("""
                {"schemaVersion":1,"editStrength":"direct_command","targetSide":"front","summary":"Add shape",
                 "actions":[{"op":"add_layer","layerId":"shape","layer":{"type":"shape","x":90,"y":10,"width":20,"height":10}}]}
                """);
        assertThatThrownBy(() -> validator.validate(outsideCard, canvas("""
                {"layers":[]}
                """), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Layer geometry must stay within the card");

        var unsafeField = parse("""
                {"schemaVersion":1,"editStrength":"direct_command","targetSide":"front","summary":"Add shape",
                 "actions":[{"op":"add_layer","layerId":"shape","layer":{"type":"shape","x":10,"y":10,"width":20,"height":10,"html":"<script>"}}]}
                """);
        assertThatThrownBy(() -> validator.validate(unsafeField, canvas("""
                {"layers":[]}
                """), true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("New layer contains an unsupported field");
    }

    private AiPatch parse(String json) {
        return schema.parse(json);
    }

    private static String canvas(String json) {
        return json;
    }
}
