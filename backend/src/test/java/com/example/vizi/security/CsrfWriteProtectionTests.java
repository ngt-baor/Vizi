package com.example.vizi.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:vizi_csrf_write_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
class CsrfWriteProtectionTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void authenticatedWriteRequestsWithoutCsrfAreForbidden() throws Exception {
        mockMvc.perform(post("/api/designs/from-template/1")
                        .with(user("owner@example.test")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/designs/1")
                        .with(user("owner@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Luxury Card","canvasJson":"{}"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/designs/1")
                        .with(user("owner@example.test")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/orders")
                        .with(user("owner@example.test"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"designId":1,"paper":"linen-300","quantity":100,"roundedCorners":false}
                                """))
                .andExpect(status().isForbidden());
    }
}
