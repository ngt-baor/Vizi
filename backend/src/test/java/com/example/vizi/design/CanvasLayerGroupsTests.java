package com.example.vizi.design;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CanvasLayerGroupsTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsLegacyRootLayersBeforePagesWhenBothArePresent() {
        var groups = CanvasLayerGroups.from(canvas("""
                {
                  "layers": [{"id":"legacy-title"}],
                  "pages": {
                    "front": {"layers":[{"id":"front-title"}]},
                    "back": {"layers":[{"id":"back-title"}]}
                  }
                }
                """));

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().side()).isNull();
        assertThat(groups.getFirst().layers().get(0).get("id").stringValue())
                .isEqualTo("legacy-title");
    }

    @Test
    void readsBothV2PageLayerGroups() {
        var groups = CanvasLayerGroups.from(canvas("""
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"layers":[{"id":"front-title"}]},
                    "back": {"layers":[{"id":"back-title"}]}
                  }
                }
                """));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).side()).isEqualTo("front");
        assertThat(groups.get(0).layers().get(0).get("id").stringValue())
                .isEqualTo("front-title");
        assertThat(groups.get(1).side()).isEqualTo("back");
        assertThat(groups.get(1).layers().get(0).get("id").stringValue())
                .isEqualTo("back-title");
    }

    @Test
    void returnsEmptyForIncompleteOrMalformedV2Pages() {
        var missingBack = canvas("""
                {"schemaVersion":2,"pages":{"front":{"layers":[]}}}
                """);
        var malformedFrontLayers = canvas("""
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"layers": {}},
                    "back": {"layers": []}
                  }
                }
                """);

        assertThat(CanvasLayerGroups.from(missingBack)).isEmpty();
        assertThat(CanvasLayerGroups.from(malformedFrontLayers)).isEmpty();
    }

    private JsonNode canvas(String json) {
        return objectMapper.readTree(json);
    }
}
