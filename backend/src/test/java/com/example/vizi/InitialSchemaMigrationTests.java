package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class InitialSchemaMigrationTests {

    private static final String PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");
    private static final String ADMIN_URL = "jdbc:postgresql://localhost:55432/postgres";

    @Test
    void migrationSqlKeepsPostgreSqlSafeDefaults() throws Exception {
        var sql = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V1__initial_schema.sql"
        )).toLowerCase(Locale.ROOT);

        assertThat(sql)
                .contains("timestamptz")
                .contains("jsonb")
                .contains("references users(id)")
                .contains("create index idx_designs_user_id")
                .doesNotContain(" drop ")
                .doesNotContain("truncate ")
                .doesNotContain("delete from ")
                .doesNotContain("serial primary key");
    }

    @Test
    void flywayMigratesEmptyPostgreSqlDatabaseAndSupportsMvpFlow() throws Exception {
        assumeTrue(PASSWORD != null && !PASSWORD.isBlank(), "local PostgreSQL password env is not available");
        assumeTrue(canConnectToLocalPostgres(), "local PostgreSQL project cluster is not running");

        var dbName = "vizi_schema_test_" + UUID.randomUUID().toString().replace("-", "");
        createDatabase(dbName);
        try {
            var url = "jdbc:postgresql://localhost:55432/" + dbName;
            var started = System.nanoTime();
            Flyway.configure()
                    .dataSource(url, "vizi", PASSWORD)
                    .cleanDisabled(false)
                    .load()
                    .migrate();
            var migrationDuration = Duration.ofNanos(System.nanoTime() - started);

            assertThat(migrationDuration).isLessThan(Duration.ofSeconds(10));
            assertTables(url);
            assertColumnTypes(url);
            assertIndexes(url);
            assertMvpAcceptanceFlow(url);
            assertForeignKeysRejectMissingOwners(url);
            assertActiveTemplateQueryIsFastEnough(url);
        } finally {
            dropDatabase(dbName);
        }
    }

    private static boolean canConnectToLocalPostgres() {
        try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD)) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private static void createDatabase(String dbName) throws SQLException {
        try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("create database " + dbName);
        }
    }

    private static void dropDatabase(String dbName) throws SQLException {
        try (var connection = DriverManager.getConnection(ADMIN_URL, "vizi", PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("""
                    select pg_terminate_backend(pid)
                    from pg_stat_activity
                    where datname = '%s' and pid <> pg_backend_pid()
                    """.formatted(dbName));
            statement.executeUpdate("drop database if exists " + dbName);
        }
    }

    private static void assertTables(String url) throws SQLException {
        var expected = Set.of(
                "users",
                "templates",
                "designs",
                "design_snapshots",
                "assets",
                "orders",
                "order_items",
                "flyway_schema_history"
        );
        try (var connection = DriverManager.getConnection(url, "vizi", PASSWORD);
             var statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select table_name
                     from information_schema.tables
                     where table_schema = 'public'
                     """)) {
            var actual = new java.util.HashSet<String>();
            while (rs.next()) {
                actual.add(rs.getString("table_name"));
            }
            assertThat(actual).containsAll(expected);
        }
    }

    private static void assertColumnTypes(String url) throws SQLException {
        try (var connection = DriverManager.getConnection(url, "vizi", PASSWORD);
             var statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select table_name, column_name, data_type
                     from information_schema.columns
                     where table_schema = 'public'
                       and (
                         column_name in ('canvas_json', 'design_snapshot_json', 'print_config_json')
                         or column_name in ('created_at', 'updated_at')
                         or column_name in ('total_amount', 'unit_price', 'subtotal')
                       )
                     """)) {
            var rows = new java.util.HashMap<String, String>();
            while (rs.next()) {
                rows.put(rs.getString("table_name") + "." + rs.getString("column_name"), rs.getString("data_type"));
            }
            assertThat(rows)
                    .containsEntry("templates.canvas_json", "jsonb")
                    .containsEntry("designs.canvas_json", "jsonb")
                    .containsEntry("design_snapshots.canvas_json", "jsonb")
                    .containsEntry("order_items.design_snapshot_json", "jsonb")
                    .containsEntry("order_items.print_config_json", "jsonb")
                    .containsEntry("orders.total_amount", "numeric")
                    .containsEntry("order_items.unit_price", "numeric")
                    .containsEntry("users.created_at", "timestamp with time zone")
                    .containsEntry("designs.updated_at", "timestamp with time zone");
        }
    }

    private static void assertIndexes(String url) throws SQLException {
        var expectedIndexes = List.of(
                "idx_designs_user_id",
                "idx_designs_template_id",
                "idx_design_snapshots_design_id",
                "idx_design_snapshots_user_id",
                "idx_assets_user_id",
                "idx_orders_user_id",
                "idx_order_items_order_id",
                "idx_order_items_design_id",
                "idx_templates_active_category"
        );
        try (var connection = DriverManager.getConnection(url, "vizi", PASSWORD);
             var statement = connection.createStatement();
             var rs = statement.executeQuery("""
                     select indexname
                     from pg_indexes
                     where schemaname = 'public'
                     """)) {
            var indexes = new java.util.HashSet<String>();
            while (rs.next()) {
                indexes.add(rs.getString("indexname"));
            }
            assertThat(indexes).containsAll(expectedIndexes);
        }
    }

    private static void assertMvpAcceptanceFlow(String url) throws SQLException {
        try (var connection = DriverManager.getConnection(url, "vizi", PASSWORD)) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                var userId = insertReturningId(statement, """
                        insert into users(email, password_hash, full_name)
                        values ('bao@example.test', '$2a$10$placeholderHash', 'Bao')
                        returning id
                        """);
                var templateId = insertReturningId(statement, """
                        insert into templates(name, category, preview_url, width_mm, height_mm, canvas_json)
                        values ('Basic card', 'business', 'https://cdn.example.test/basic.png', 90, 54, '{"layers":[]}'::jsonb)
                        returning id
                        """);
                var designId = insertReturningId(statement, """
                        insert into designs(user_id, template_id, name, canvas_json, width_mm, height_mm)
                        values (%d, %d, 'Bao card', '{"layers":[{"type":"text","text":"Bao"}]}'::jsonb, 90, 54)
                        returning id
                        """.formatted(userId, templateId));
                insertReturningId(statement, """
                        insert into design_snapshots(design_id, user_id, reason, canvas_json)
                        values (%d, %d, 'save', '{"layers":[{"type":"text","text":"Bao"}]}'::jsonb)
                        returning id
                        """.formatted(designId, userId));
                var orderId = insertReturningId(statement, """
                        insert into orders(user_id, status, total_amount, customer_note)
                        values (%d, 'PENDING_PAYMENT', 120000, 'uat smoke order')
                        returning id
                        """.formatted(userId));
                insertReturningId(statement, """
                        insert into order_items(order_id, design_id, design_snapshot_json, print_config_json, quantity, unit_price, subtotal)
                        values (%d, %d, '{"layers":[{"type":"text","text":"Bao"}]}'::jsonb, '{"paper":"matte"}'::jsonb, 100, 1200, 120000)
                        returning id
                        """.formatted(orderId, designId));

                try (var rs = statement.executeQuery("""
                        select o.status, oi.quantity, d.name
                        from orders o
                        join order_items oi on oi.order_id = o.id
                        join designs d on d.id = oi.design_id
                        where o.user_id = %d
                        """.formatted(userId))) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("PENDING_PAYMENT");
                    assertThat(rs.getInt("quantity")).isEqualTo(100);
                    assertThat(rs.getString("name")).isEqualTo("Bao card");
                }
                connection.commit();
            } catch (Throwable ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private static void assertForeignKeysRejectMissingOwners(String url) {
        assertThatThrownBy(() -> {
            try (var connection = DriverManager.getConnection(url, "vizi", PASSWORD);
                 var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        insert into designs(user_id, name, canvas_json, width_mm, height_mm)
                        values (999999, 'Invalid owner', '{}'::jsonb, 90, 54)
                        """);
            }
        }).hasMessageContaining("violates foreign key constraint");
    }

    private static void assertActiveTemplateQueryIsFastEnough(String url) throws SQLException {
        try (var connection = DriverManager.getConnection(url, "vizi", PASSWORD);
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into templates(name, category, width_mm, height_mm, canvas_json, active)
                    select 'Template ' || n, 'business', 90, 54, '{"layers":[]}'::jsonb, true
                    from generate_series(1, 100) n
                    """);
            var started = System.nanoTime();
            try (var rs = statement.executeQuery("""
                    select id, name
                    from templates
                    where active = true and category = 'business'
                    order by id
                    limit 20
                    """)) {
                var count = 0;
                while (rs.next()) {
                    count++;
                }
                assertThat(count).isEqualTo(20);
            }
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(500));
        }
    }

    private static long insertReturningId(java.sql.Statement statement, String sql) throws SQLException {
        try (var rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }
}
