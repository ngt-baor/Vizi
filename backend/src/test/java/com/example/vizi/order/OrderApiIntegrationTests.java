package com.example.vizi.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import com.example.vizi.auth.User;
import com.example.vizi.auth.UserRepository;
import com.example.vizi.design.DesignRepository;
import com.example.vizi.template.Template;
import com.example.vizi.template.TemplateRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@EnabledIf("localPostgresAvailable")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OrderApiIntegrationTests {

    private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:55432/postgres";
    private static final String DB_NAME = "vizi_order_api_test_" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    DesignRepository designRepository;

    @Autowired
    TemplateRepository templateRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

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
        orderRepository.deleteAll();
        designRepository.deleteAll();
        templateRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanCreateOrderWithOneOrderItem() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var template = templateRepository.save(new Template(
                "Order Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Order\"}]}",
                true
        ));
        var createDesignResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createDesignResponse.getResponse().getContentAsString(), "$.id");

        var createOrderResponse = mockMvc.perform(post("/api/orders")
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "designId": %d,
                                  "paper": "linen-300",
                                  "quantity": 500,
                                  "roundedCorners": true,
                                  "customerNote": "Leave at front desk"
                                }
                                """.formatted(designId.longValue())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(1550000.00))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(500))
                .andExpect(jsonPath("$.items[0].subtotal").value(1550000.00))
                .andReturn();

        Number orderId = JsonPath.read(createOrderResponse.getResponse().getContentAsString(), "$.id");
        var order = orderRepository.findById(orderId.longValue()).orElseThrow();
        assertThat(order.totalAmount()).isEqualByComparingTo("1550000.00");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from order_items where order_id = ?",
                Long.class,
                orderId.longValue()
        )).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "select design_snapshot_json->'layers'->0->>'text' from order_items where order_id = ?",
                String.class,
                orderId.longValue()
        )).isEqualTo("Order");
        assertThat(jdbcTemplate.queryForObject(
                "select print_config_json->>'paper' from order_items where order_id = ?",
                String.class,
                orderId.longValue()
        )).isEqualTo("linen-300");
        assertThat(jdbcTemplate.queryForObject(
                "select print_config_json->>'widthMm' from order_items where order_id = ?",
                String.class,
                orderId.longValue()
        )).isEqualTo("90.00");
        assertThat(jdbcTemplate.queryForObject(
                "select print_config_json->>'heightMm' from order_items where order_id = ?",
                String.class,
                orderId.longValue()
        )).isEqualTo("54.00");
    }

    @Test
    void orderSnapshotDoesNotChangeWhenDesignIsEditedAfterCheckout() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var template = templateRepository.save(new Template(
                "Snapshot Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Original\"}]}",
                true
        ));
        var createDesignResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createDesignResponse.getResponse().getContentAsString(), "$.id");

        var createOrderResponse = mockMvc.perform(post("/api/orders")
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "designId": %d,
                                  "paper": "matte-350",
                                  "quantity": 100,
                                  "roundedCorners": false
                                }
                                """.formatted(designId.longValue())))
                .andExpect(status().isCreated())
                .andReturn();
        Number orderId = JsonPath.read(createOrderResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/designs/" + designId.longValue())
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Changed Card",
                                  "canvasJson": "{\\"layers\\":[{\\"type\\":\\"text\\",\\"text\\":\\"Changed\\"}]}"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select canvas_json->'layers'->0->>'text' from designs where id = ?",
                String.class,
                designId.longValue()
        )).isEqualTo("Changed");
        assertThat(jdbcTemplate.queryForObject(
                "select design_snapshot_json->'layers'->0->>'text' from order_items where order_id = ?",
                String.class,
                orderId.longValue()
        )).isEqualTo("Original");
    }

    @Test
    void anotherUserCannotCreateOrderFromOwnedDesign() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        userRepository.save(new User("other@example.test", "test-hash", "Other"));
        var template = templateRepository.save(new Template(
                "Private Order Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Private\"}]}",
                true
        ));
        var createDesignResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createDesignResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/orders")
                        .with(user("other@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "designId": %d,
                                  "paper": "matte-350",
                                  "quantity": 100,
                                  "roundedCorners": false
                                }
                                """.formatted(designId.longValue())))
                .andExpect(status().isNotFound());
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void unsupportedPaperIsRejected() throws Exception {
        userRepository.save(new User("owner@example.test", "test-hash", "Owner"));
        var template = templateRepository.save(new Template(
                "Paper Check Card",
                "business",
                null,
                new BigDecimal("90.00"),
                new BigDecimal("54.00"),
                "{\"layers\":[{\"type\":\"text\",\"text\":\"Paper\"}]}",
                true
        ));
        var createDesignResponse = mockMvc.perform(post("/api/designs/from-template/" + template.id())
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        Number designId = JsonPath.read(createDesignResponse.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/orders")
                        .with(user("owner@example.test"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "designId": %d,
                                  "paper": "unknown",
                                  "quantity": 100,
                                  "roundedCorners": false
                                }
                                """.formatted(designId.longValue())))
                .andExpect(status().isBadRequest());
        assertThat(orderRepository.count()).isZero();
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
