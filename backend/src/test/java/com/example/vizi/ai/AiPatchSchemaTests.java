package com.example.vizi.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class AiPatchSchemaTests {

    private final AiPatchSchema schema = new AiPatchSchema(new ObjectMapper());

    @Test
    void parsesStructurallyValidPatchBeforeActionWhitelistExists() {
        var patch = schema.parse("""
                {
                  "schemaVersion": 1,
                  "editStrength": "light",
                  "targetSide": "front",
                  "summary": "Refine the headline.",
                  "actions": [
                    {
                      "op": "future_operation",
                      "layerId": "headline_text",
                      "text": "Luxury design studio"
                    }
                  ]
                }
                """);

        assertThat(patch.schemaVersion()).isEqualTo(1);
        assertThat(patch.editStrength()).isEqualTo(AiEditStrength.LIGHT);
        assertThat(patch.targetSide()).isEqualTo(AiTargetSide.FRONT);
        assertThat(patch.actions()).singleElement().satisfies(action -> {
            assertThat(action.op()).isEqualTo("future_operation");
            assertThat(action.layerId()).isEqualTo("headline_text");
            assertThat(action.text()).isEqualTo("Luxury design studio");
        });
    }

    @Test
    void rejectsMalformedRootAndUnknownFields() {
        assertThatThrownBy(() -> schema.parse("""
                {"schemaVersion":1,"editStrength":"light","targetSide":"front","summary":"x"}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patch actions must contain 1 to 20 items");

        assertThatThrownBy(() -> schema.parse("""
                {
                  "schemaVersion": 1,
                  "editStrength": "light",
                  "targetSide": "front",
                  "summary": "x",
                  "actions": [{"op":"update_text","layerId":"title","text":"New","html":"<script>"}]
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Patch action 0 contains an unsupported field");
    }

    @Test
    void rejectsUnsupportedVersionAndInvalidActionShape() {
        assertThatThrownBy(() -> schema.parse("""
                {
                  "schemaVersion": 2,
                  "editStrength": "light",
                  "targetSide": "front",
                  "summary": "x",
                  "actions": [{"op":"update_text","layerId":"title","text":"New"}]
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported schemaVersion");

        assertThatThrownBy(() -> schema.parse("""
                {
                  "schemaVersion": 1,
                  "editStrength": "strong",
                  "targetSide": "front",
                  "summary": "x",
                  "actions": [{"op":"update_text","layerId":"9-title"}]
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("editStrength is invalid");
    }
}
