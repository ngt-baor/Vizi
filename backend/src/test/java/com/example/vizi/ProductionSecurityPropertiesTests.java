package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ProductionSecurityPropertiesTests {

    @Test
    void productionSessionCookieSupportsCrossSiteHttpsFrontend() throws IOException {
        assertThat(readResource("/application-prod.properties"))
                .contains("server.servlet.session.cookie.http-only=true")
                .contains("server.servlet.session.cookie.same-site=none")
                .contains("server.servlet.session.cookie.secure=true");
    }

    @Test
    void persistentSessionsUseJdbcWithThirtyDayLifetime() throws IOException {
        assertThat(readResource("/application.properties"))
                .contains("spring.session.timeout=30d")
                .contains("spring.session.jdbc.initialize-schema=never")
                .contains("server.servlet.session.cookie.max-age=30d")
                .contains("server.servlet.session.cookie.http-only=true");
        assertThat(readResource("/application-test.properties"))
                .contains("spring.session.jdbc.initialize-schema=always");
    }

    private String readResource(String path) throws IOException {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
