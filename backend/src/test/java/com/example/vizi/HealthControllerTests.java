package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTests {

    @Test
    void healthReturnsUpStatus() {
        var response = new HealthController().health();

        assertThat(response)
                .containsEntry("status", "UP")
                .containsEntry("service", "vizi-backend")
                .containsKey("time");
    }
}
