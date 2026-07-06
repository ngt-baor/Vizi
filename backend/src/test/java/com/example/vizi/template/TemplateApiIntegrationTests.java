package com.example.vizi.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIf("localPostgresAvailable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class TemplateApiIntegrationTests {

    private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:55432/postgres";
    private static final String DB_NAME = "vizi_template_api_test_" + UUID.randomUUID().toString().replace("-", "");

    @LocalServerPort
    int port;

    @Autowired
    TemplateRepository templateRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) throws SQLException {
        createDatabase();
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:55432/" + DB_NAME);
        registry.add("spring.datasource.username", () -> "vizi");
        registry.add("spring.datasource.password", () -> PASSWORD);
    }

    @AfterAll
    static void dropDatabase() throws SQLException {
        if (localPostgresAvailable()) {
            try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("""
                        select pg_terminate_backend(pid)
                        from pg_stat_activity
                        where datname = '%s' and pid <> pg_backend_pid()
                        """.formatted(DB_NAME));
                statement.executeUpdate("drop database if exists " + DB_NAME);
            }
        }
    }

    @BeforeEach
    void clearTemplates() {
        templateRepository.deleteAll();
    }

    @Test
    void listTemplatesReturnsOnlyActiveTemplatesSortedById() throws Exception {
        var activeA = templateRepository.save(template(
                "Basic Card",
                "business",
                "https://cdn.example.test/basic.png",
                true
        ));
        templateRepository.save(template(
                "Hidden Card",
                "business",
                "https://cdn.example.test/hidden.png",
                false
        ));
        var activeB = templateRepository.save(template(
                "Spa Card",
                "spa",
                "https://cdn.example.test/spa.png",
                true
        ));

        var response = get("/api/templates");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":" + activeA.id());
        assertThat(response.body()).contains("\"id\":" + activeB.id());
        assertThat(response.body()).doesNotContain("Hidden Card");
        assertThat(response.body().indexOf("Basic Card")).isLessThan(response.body().indexOf("Spa Card"));
    }

    @Test
    void getTemplateReturnsActiveTemplateDetailAndHidesInactiveTemplates() throws Exception {
        var active = templateRepository.save(template(
                "Basic Card",
                "business",
                "https://cdn.example.test/basic.png",
                true
        ));
        var inactive = templateRepository.save(template(
                "Hidden Card",
                "business",
                "https://cdn.example.test/hidden.png",
                false
        ));

        var activeResponse = get("/api/templates/" + active.id());
        var inactiveResponse = get("/api/templates/" + inactive.id());
        var missingResponse = get("/api/templates/999999");

        assertThat(activeResponse.statusCode()).isEqualTo(200);
        assertThat(activeResponse.body()).contains("\"id\":" + active.id());
        assertThat(activeResponse.body()).contains("layers");

        assertThat(inactiveResponse.statusCode()).isEqualTo(404);
        assertThat(inactiveResponse.body()).contains("Template not found");
        assertThat(missingResponse.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Template template(String name, String category, String previewUrl, boolean active) {
        return new Template(
                name,
                category,
                previewUrl,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[]}",
                active
        );
    }

    static boolean localPostgresAvailable() {
        if (PASSWORD == null || PASSWORD.isBlank()) {
            return false;
        }
        try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD)) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private static void createDatabase() throws SQLException {
        try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("create database " + DB_NAME);
        }
    }
}
