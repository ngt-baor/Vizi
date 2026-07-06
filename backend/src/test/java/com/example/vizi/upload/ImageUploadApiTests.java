package com.example.vizi.upload;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vizi_upload_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
class ImageUploadApiTests {

    private static final Path UPLOAD_DIR = createUploadDir();

    @Autowired
    MockMvc mockMvc;

    @DynamicPropertySource
    static void uploadProperties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.image-dir", () -> UPLOAD_DIR.toString());
    }

    @AfterAll
    static void cleanUploads() throws IOException {
        if (Files.exists(UPLOAD_DIR)) {
            try (var paths = Files.walk(UPLOAD_DIR)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void authenticatedUserCanUploadPngAndReadReturnedUrl() throws Exception {
        var response = mockMvc.perform(multipart("/api/uploads/images")
                        .file(pngFile("logo.png"))
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value(org.hamcrest.Matchers.endsWith(".png")))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(pngBytes().length))
                .andExpect(jsonPath("$.storageKey").value(org.hamcrest.Matchers.startsWith("images/")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/uploads/images/")))
                .andReturn();

        String url = JsonPath.read(response.getResponse().getContentAsString(), "$.url");
        String fileName = JsonPath.read(response.getResponse().getContentAsString(), "$.fileName");
        assertThat(UPLOAD_DIR.resolve(fileName)).exists().isRegularFile();

        mockMvc.perform(get(url).with(user("owner@example.test")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    void uploadRequiresAuthenticatedSessionAndCsrf() throws Exception {
        mockMvc.perform(multipart("/api/uploads/images")
                        .file(pngFile("logo.png"))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/uploads/images")
                        .file(pngFile("logo.png"))
                        .with(user("owner@example.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void svgAndMismatchedContentAreRejected() throws Exception {
        var svg = new MockMultipartFile(
                "file",
                "attack.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes()
        );
        mockMvc.perform(multipart("/api/uploads/images")
                        .file(svg)
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PNG, JPG, or WebP images are allowed"));

        var fakePng = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                "not a png".getBytes()
        );
        mockMvc.perform(multipart("/api/uploads/images")
                        .file(fakePng)
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Image content does not match the file type"));
    }

    @Test
    void oversizedImageIsRejected() throws Exception {
        byte[] oversized = new byte[(5 * 1024 * 1024) + 1];
        System.arraycopy(pngBytes(), 0, oversized, 0, pngBytes().length);
        var file = new MockMultipartFile("file", "large.png", "image/png", oversized);

        mockMvc.perform(multipart("/api/uploads/images")
                        .file(file)
                        .with(user("owner@example.test"))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Image must be 5 MB or smaller"));
    }

    private static MockMultipartFile pngFile(String originalName) {
        return new MockMultipartFile("file", originalName, "image/png", pngBytes());
    }

    private static byte[] pngBytes() {
        return new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00
        };
    }

    private static Path createUploadDir() {
        try {
            return Files.createTempDirectory("vizi-upload-test-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create upload test directory", exception);
        }
    }
}
