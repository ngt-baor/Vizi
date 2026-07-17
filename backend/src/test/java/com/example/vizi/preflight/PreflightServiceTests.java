package com.example.vizi.preflight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class PreflightServiceTests {

    private final PreflightService preflightService = new PreflightService(new ObjectMapper());

    @Test
    void emptyCanvasReturnsBlockingError() {
        var report = preflightService.check("{\"layers\":[]}", 90, 54);

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).containsExactly(new PreflightIssue(
                "ERROR",
                "EMPTY_CANVAS",
                "Canvas must contain at least one layer.",
                null
        ));
    }

    @Test
    void nonEmptyCanvasPassesBasePreflight() {
        var report = preflightService.check("{\"layers\":[{\"type\":\"text\",\"text\":\"Vizi\"}]}", 90, 54);

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void invalidCanvasReturnsBlockingError() {
        var report = preflightService.check("{broken", 90, 54);

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).extracting(PreflightIssue::code)
                .containsExactly("INVALID_CANVAS");
    }

    @Test
    void textOutsideSafeZoneReturnsBlockingError() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"text\",\"x\":1,\"y\":1,\"width\":30,\"height\":10}]}",
                90,
                54
        );

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).containsExactly(new PreflightIssue(
                "ERROR",
                "LAYER_OUTSIDE_SAFE_ZONE",
                "Text layers must stay inside the 3 mm safe zone.",
                0
        ));
    }

    @Test
    void qrOutsideSafeZoneReturnsNonBlockingWarning() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"qr\",\"x\":0,\"y\":0,\"width\":20,\"height\":20}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).containsExactly(new PreflightIssue(
                "WARNING",
                "LAYER_OUTSIDE_SAFE_ZONE",
                "Layer extends outside the 3 mm safe zone.",
                0
        ));
    }

    @Test
    void imageOutsideSafeZoneReturnsNonBlockingWarning() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"image\",\"x\":0,\"y\":0,\"width\":30,\"height\":30}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).containsExactly(new PreflightIssue(
                "WARNING",
                "LAYER_OUTSIDE_SAFE_ZONE",
                "Layer extends outside the 3 mm safe zone.",
                0
        ));
    }

    @Test
    void fullCanvasBackgroundDoesNotTriggerSafeZoneWarning() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"rect\",\"x\":0,\"y\":0,\"width\":100,\"height\":100}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void layerInsideSafeZonePasses() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"text\",\"x\":10,\"y\":10,\"width\":40,\"height\":20}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void lowResolutionImageReturnsNonBlockingWarning() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"image\",\"x\":10,\"y\":10,\"width\":40,\"height\":40,"
                        + "\"pixelWidth\":100,\"pixelHeight\":100}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).containsExactly(new PreflightIssue(
                "WARNING",
                "LOW_IMAGE_RESOLUTION",
                "Image resolution is below 150 DPI at the current print size.",
                0
        ));
    }

    @Test
    void sufficientImageResolutionPasses() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"image\",\"x\":10,\"y\":10,\"width\":40,\"height\":40,"
                        + "\"pixelWidth\":1200,\"pixelHeight\":1200}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void imageWithoutPixelMetadataKeepsBackwardCompatibility() {
        var report = preflightService.check(
                "{\"layers\":[{\"type\":\"image\",\"x\":10,\"y\":10,\"width\":40,\"height\":40}]}",
                90,
                54
        );

        assertThat(report.valid()).isTrue();
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void v2FrontAndBackPagesAreChecked() {
        var report = preflightService.check("""
                {
                  "schemaVersion": 2,
                  "pages": {
                    "front": {"id": "front", "layers": [
                      {"type": "text", "x": 1, "y": 1, "width": 30, "height": 10}
                    ]},
                    "back": {"id": "back", "layers": [
                      {"type": "image", "x": 10, "y": 10, "width": 40, "height": 40,
                       "pixelWidth": 100, "pixelHeight": 100}
                    ]}
                  }
                }
                """, 90, 54);

        assertThat(report.valid()).isFalse();
        assertThat(report.issues()).extracting(PreflightIssue::code)
                .containsExactly("LAYER_OUTSIDE_SAFE_ZONE", "LOW_IMAGE_RESOLUTION");
        assertThat(report.issues()).extracting(PreflightIssue::side)
                .containsExactly("front", "back");
    }
}
