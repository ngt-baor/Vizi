package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ProductionSecurityPropertiesTests {

    @Test
    void productionSessionCookieSupportsCrossSiteHttpsFrontend() throws IOException {
        try (var input = getClass().getResourceAsStream("/application-prod.properties")) {
            assertThat(input).isNotNull();
            var properties = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(properties)
                    .contains("server.servlet.session.cookie.http-only=true")
                    .contains("server.servlet.session.cookie.same-site=none")
                    .contains("server.servlet.session.cookie.secure=true");
        }
    }
}
