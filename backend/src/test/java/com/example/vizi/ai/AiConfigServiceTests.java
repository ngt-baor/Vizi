package com.example.vizi.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AiConfigServiceTests {

    @Test
    void missingGeminiKeyReturnsClearServiceUnavailableError() {
        var service = new AiConfigService("", "gemini-3.1-flash-lite", "gemini-3.1-flash-lite-image");

        assertThatThrownBy(service::requireConfigured)
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).isEqualTo("GEMINI_API_KEY is not configured");
                });
    }

    @Test
    void configuredStatusDoesNotExposeGeminiKey() {
        var service = new AiConfigService("test-secret-key", "text-model", "image-model");

        var status = service.requireConfigured();

        assertThat(status.configured()).isTrue();
        assertThat(status.textModel()).isEqualTo("text-model");
        assertThat(status.imageModel()).isEqualTo("image-model");
        assertThat(status.toString()).doesNotContain("test-secret-key");
    }
}
