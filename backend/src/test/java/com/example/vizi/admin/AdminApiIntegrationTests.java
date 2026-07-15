package com.example.vizi.admin;

import com.example.vizi.auth.User;
import com.example.vizi.auth.UserRepository;
import com.example.vizi.design.DesignRepository;
import com.example.vizi.template.Template;
import com.example.vizi.template.TemplateRepository;
import com.jayway.jsonpath.JsonPath;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@EnabledIf("localPostgresAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminApiIntegrationTests {

    private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:55432/postgres";
    private static final String DB_NAME = "vizi_admin_api_test_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DesignRepository designRepository;

    @Autowired
    TemplateRepository templateRepository;

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

    @BeforeEach
    void clearData() {
        designRepository.deleteAll();
        templateRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminCanViewAnotherUsersAlbum() throws Exception {
        userRepository.save(new User("admin@example.test", "test-hash", "Admin", "ADMIN"));
        var customer = userRepository.save(new User("customer@example.test", "test-hash", "Customer"));
        var template = templateRepository.save(new Template(
                "Album Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Album\"}]}",
                true
        ));

        var createResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("customer@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/admin/users/" + customer.id() + "/designs")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(designId.longValue()))
                .andExpect(jsonPath("$[0].userId").value(customer.id()))
                .andExpect(jsonPath("$[0].name").value("Album Card"));
    }

    @Test
    void normalUserCannotViewAnotherUsersAlbumThroughAdminApi() throws Exception {
        var customer = userRepository.save(new User("customer@example.test", "test-hash", "Customer"));
        userRepository.save(new User("other@example.test", "test-hash", "Other"));

        mockMvc.perform(get("/api/admin/users/" + customer.id() + "/designs")
                        .with(user("other@example.test")))
                .andExpect(status().isForbidden());
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