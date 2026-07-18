package com.example.vizi.paper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import com.example.vizi.auth.User;
import com.example.vizi.auth.UserRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@EnabledIf("localPostgresAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PaperStockApiIntegrationTests {

    private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:55432/postgres";
    private static final String DB_NAME = "vizi_paper_api_test_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

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

    @Test
    void publicCatalogAndAdminCrudExposePaperAvailability() throws Exception {
        userRepository.save(new User("admin-paper@example.test", "test-hash", "Admin Paper", "ADMIN"));

        mockMvc.perform(get("/api/papers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].code").value("matte-350"))
                .andExpect(jsonPath("$[0].name").value("Couche 350gsm"))
                .andExpect(jsonPath("$[0].status").value("IN_STOCK"));

        var createResponse = mockMvc.perform(post("/api/admin/papers")
                        .with(user("admin-paper@example.test").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "metallic-300",
                                  "name": "Metallic 300gsm",
                                  "description": "Pearlescent finish",
                                  "gsm": 300,
                                  "pricePer100": 360000,
                                  "status": "IN_STOCK",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("metallic-300"))
                .andReturn();
        Number paperId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/admin/papers/" + paperId.longValue())
                        .with(user("admin-paper@example.test").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "metallic-300",
                                  "name": "Metallic 300gsm",
                                  "description": "Pearlescent finish",
                                  "gsm": 300,
                                  "pricePer100": 365000,
                                  "status": "OUT_OF_STOCK",
                                  "active": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricePer100").value(365000))
                .andExpect(jsonPath("$.status").value("OUT_OF_STOCK"));

        mockMvc.perform(delete("/api/admin/papers/" + paperId.longValue())
                        .with(user("admin-paper@example.test").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void normalUserCannotManagePaperCatalog() throws Exception {
        userRepository.save(new User("paper-user@example.test", "test-hash", "Paper User"));

        mockMvc.perform(get("/api/admin/papers")
                        .with(user("paper-user@example.test")))
                .andExpect(status().isForbidden());
    }

    static boolean localPostgresAvailable() {
        if (PASSWORD == null || PASSWORD.isBlank()) {
            return false;
        }
        try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD)) {
            return connection.isValid(2);
        } catch (SQLException exception) {
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