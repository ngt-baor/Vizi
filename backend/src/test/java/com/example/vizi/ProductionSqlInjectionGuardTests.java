package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProductionSqlInjectionGuardTests {

    private static final List<String> RAW_SQL_MARKERS = List.of(
            "java.sql.Statement",
            ".createStatement(",
            ".prepareStatement(",
            "createNativeQuery(",
            "JdbcTemplate",
            "NamedParameterJdbcTemplate",
            "EntityManager",
            "@Query");

    @Test
    void productionCodeDoesNotUseRawSqlApis() throws IOException {
        Path sourceRoot = Path.of("src/main/java");

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            List<String> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(ProductionSqlInjectionGuardTests::violationsIn)
                    .toList();

            assertThat(violations)
                    .as("Production database access should stay on Spring Data repositories or explicit parameterized code")
                    .isEmpty();
        }
    }

    private static Stream<String> violationsIn(Path path) {
        try {
            String content = Files.readString(path);
            return RAW_SQL_MARKERS.stream()
                    .filter(content::contains)
                    .map(marker -> path + " contains " + marker);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
