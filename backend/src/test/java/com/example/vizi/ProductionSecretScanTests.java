package com.example.vizi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ProductionSecretScanTests {

    private static final List<Path> SCAN_ROOTS = List.of(
            Path.of("src/main/java"),
            Path.of("src/main/resources"),
            Path.of("env.production.example"),
            Path.of("../frontend/src")
    );

    private static final List<Pattern> PROVIDER_SECRET_PATTERNS = List.of(
            Pattern.compile("AIza[0-9A-Za-z_-]{35}"),
            Pattern.compile("AQ\\.[0-9A-Za-z_-]{20,}"),
            Pattern.compile("gh[pousr]_[0-9A-Za-z_]{36,}"),
            Pattern.compile("sk-[A-Za-z0-9_-]{32,}")
    );

    private static final Pattern CODE_LITERAL_SECRET = Pattern.compile(
            "(?i)(api[_-]?key|token|secret|password)[A-Za-z0-9_.-]*\\s*=\\s*[\"'](?!\\$\\{|<|REPLACE|CHANGE|your_|example|placeholder)[^\"']{12,}[\"']"
    );

    private static final Pattern CONFIG_SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(api[_-]?key|token|secret|password)[A-Za-z0-9_.-]*\\s*[:=]\\s*(?!\\$\\{|<|REPLACE|CHANGE|your_|example|placeholder|$)[^\\s#]{8,}"
    );

    @Test
    void productionFilesDoNotContainHardcodedSecrets() throws IOException {
        List<String> violations = SCAN_ROOTS.stream()
                .flatMap(ProductionSecretScanTests::scanRoot)
                .flatMap(ProductionSecretScanTests::violationsIn)
                .toList();

        assertThat(violations)
                .as("Production source/config files must use env placeholders instead of hardcoded secrets")
                .isEmpty();
    }

    private static Stream<Path> scanRoot(Path root) {
        if (!Files.exists(root)) {
            return Stream.empty();
        }
        try {
            if (Files.isRegularFile(root)) {
                return Stream.of(root);
            }
            return Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(ProductionSecretScanTests::isTextFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot scan " + root, exception);
        }
    }

    private static boolean isTextFile(Path path) {
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".properties")
                || name.endsWith(".sql")
                || name.endsWith(".ts")
                || name.endsWith(".vue")
                || name.endsWith(".css")
                || name.endsWith(".json")
                || name.endsWith(".example");
    }

    private static Stream<String> violationsIn(Path path) {
        try {
            var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return IntStream.range(0, lines.size())
                    .mapToObj(index -> violationInLine(path, index + 1, lines.get(index)))
                    .flatMap(List::stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private static List<String> violationInLine(Path path, int lineNumber, String line) {
        if (line.isBlank() || line.trim().startsWith("//") || line.trim().startsWith("#")) {
            return List.of();
        }
        var violations = PROVIDER_SECRET_PATTERNS.stream()
                .filter(pattern -> pattern.matcher(line).find())
                .map(pattern -> path + ":" + lineNumber + " matches provider secret pattern")
                .toList();
        if (!violations.isEmpty()) {
            return violations;
        }
        var assignmentPattern = isConfigLike(path) ? CONFIG_SECRET_ASSIGNMENT : CODE_LITERAL_SECRET;
        if (assignmentPattern.matcher(line).find()) {
            return List.of(path + ":" + lineNumber + " contains hardcoded secret-like assignment");
        }
        return List.of();
    }

    private static boolean isConfigLike(Path path) {
        var name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".properties") || name.endsWith(".example") || name.endsWith(".env");
    }
}
