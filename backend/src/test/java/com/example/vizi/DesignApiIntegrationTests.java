package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
class DesignApiIntegrationTests {

    private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:55432/postgres";
    private static final String DB_NAME = "vizi_design_api_test_" + UUID.randomUUID().toString().replace("-", "");

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
    void ownerCanCreateDesignFromTemplateAndReadItBack() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var template = templateRepository.save(new Template(
                "Luxury Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Studio\"}]}",
                true
        ));

        var createResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Luxury Card"))
                .andReturn();
        String createdCanvas = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.canvasJson");
        assertThat((String) JsonPath.read(createdCanvas, "$.layers[0].text")).isEqualTo("Studio");
        Number designId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        var getResponse = mockMvc.perform(get("/api/designs/" + designId)
                        .with(user("owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(template.id()))
                .andExpect(jsonPath("$.widthMm").value(90.00))
                .andExpect(jsonPath("$.heightMm").value(54.00))
                .andReturn();
        String loadedCanvas = JsonPath.read(getResponse.getResponse().getContentAsString(), "$.canvasJson");
        assertThat((String) JsonPath.read(loadedCanvas, "$.layers[0].text")).isEqualTo("Studio");
    }

    @Test
    void ownerCanUpdateCanvasAndReadSavedContent() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var template = templateRepository.save(new Template(
                "Luxury Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Studio\"}]}",
                true
        ));
        var createResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        var updateResponse = mockMvc.perform(put("/api/designs/" + designId)
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Saved Luxury Card",
                                  "canvasJson": "{\\"layers\\":[{\\"type\\":\\"text\\",\\"text\\":\\"Saved Studio\\"}]}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Saved Luxury Card"))
                .andReturn();
        String savedCanvas = JsonPath.read(updateResponse.getResponse().getContentAsString(), "$.canvasJson");
        assertThat((String) JsonPath.read(savedCanvas, "$.layers[0].text")).isEqualTo("Saved Studio");

        var getResponse = mockMvc.perform(get("/api/designs/" + designId)
                        .with(user("owner@example.test")))
                .andExpect(status().isOk())
                .andReturn();
        String loadedCanvas = JsonPath.read(getResponse.getResponse().getContentAsString(), "$.canvasJson");
        assertThat((String) JsonPath.read(loadedCanvas, "$.layers[0].text")).isEqualTo("Saved Studio");
    }

    @Test
    void ownerCanListOnlyTheirDesignsNewestFirst() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        userRepository.save(new User("other@example.test", "test-hash", "Other"));
        var template = templateRepository.save(new Template(
                "List Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[]}",
                true
        ));

        var firstResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number firstDesignId = JsonPath.read(firstResponse.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("other@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/designs/" + firstDesignId)
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Newest Owner Draft",
                                  "canvasJson": "{\\"layers\\":[]}"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/designs")
                        .with(user("owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Newest Owner Draft"))
                .andExpect(jsonPath("$[0].canvasJson").doesNotExist())
                .andExpect(jsonPath("$[1].name").value("List Card"));
    }

    @Test
    void anotherUserCannotReadOrUpdateOwnedDesign() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        userRepository.save(new User("other@example.test", "test-hash", "Other"));
        var template = templateRepository.save(new Template(
                "Private Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[]}",
                true
        ));
        var createResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/designs/" + designId)
                        .with(user("other@example.test")))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/designs/" + designId)
                        .with(user("other@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Stolen Card",
                                  "canvasJson": "{\\"layers\\":[]}"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanDeleteDesignAndOtherUserCannotDeleteIt() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        userRepository.save(new User("other@example.test", "test-hash", "Other"));
        var template = templateRepository.save(new Template(
                "Delete Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[]}",
                true
        ));
        var createResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(delete("/api/designs/" + designId)
                        .with(user("other@example.test"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/designs/" + designId)
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/designs/" + designId)
                        .with(user("owner@example.test")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/designs")
                        .with(user("owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidCanvasIsRejectedWithoutOverwritingSavedDesign() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var template = templateRepository.save(new Template(
                "Validated Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Original\"}]}",
                true
        ));
        var createResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/designs/" + designId)
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid Card",
                                  "canvasJson": "{}"
                                }
                                """))
                .andExpect(status().isBadRequest());

        var getResponse = mockMvc.perform(get("/api/designs/" + designId)
                        .with(user("owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Validated Card"))
                .andReturn();
        String loadedCanvas = JsonPath.read(getResponse.getResponse().getContentAsString(), "$.canvasJson");
        assertThat((String) JsonPath.read(loadedCanvas, "$.layers[0].text")).isEqualTo("Original");
    }

    @Test
    void inactiveAndMissingTemplatesCannotCreateDesigns() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var inactive = templateRepository.save(new Template(
                "Hidden Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[]}",
                false
        ));

        mockMvc.perform(post("/api/designs/from-template/" + inactive.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/designs/from-template/999999")
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
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
