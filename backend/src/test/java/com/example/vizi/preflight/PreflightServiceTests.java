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
}
