package com.example.vizi.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTests {

    @Test
    void illegalArgumentReturnsBadRequestJson() {
        var request = new MockHttpServletRequest("POST", "/api/test");

        var response = new ApiExceptionHandler().badRequest(
                new IllegalArgumentException("Invalid request"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
        assertThat(response.getBody().path()).isEqualTo("/api/test");
    }

    @Test
    void noResourceReturnsNotFoundJson() {
        var request = new MockHttpServletRequest("GET", "/api/missing");

        var response = new ApiExceptionHandler().notFound(
                new NoResourceFoundException(HttpMethod.GET, "/api/missing", "No static resource"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Not Found");
        assertThat(response.getBody().path()).isEqualTo("/api/missing");
    }

    @Test
    void unexpectedExceptionReturnsGenericMessageAndTraceId() {
        var request = new MockHttpServletRequest("GET", "/api/failure");

        var response = new ApiExceptionHandler().unexpected(
                new IllegalStateException("database details must not reach the client"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().message()).doesNotContain("database details");
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    @Test
    void responseStatusServerErrorHidesInternalReasonAndKeepsTraceId() {
        var request = new MockHttpServletRequest("POST", "/api/designs");

        var response = new ApiExceptionHandler().responseStatus(
                new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "database details",
                        new IllegalStateException("database details")
                ),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().message()).doesNotContain("database details");
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    @Test
    void responseStatusServerErrorWithoutCauseAlsoHidesInternalReason() {
        var request = new MockHttpServletRequest("GET", "/api/failure");

        var response = new ApiExceptionHandler().responseStatus(
                new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "database details"),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().message()).doesNotContain("database details");
        assertThat(response.getBody().traceId()).isNotBlank();
    }
}
