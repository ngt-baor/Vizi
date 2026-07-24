package com.example.vizi.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiErrorControllerTests {

    @Test
    void errorReturnsJsonBodyForNotFound() {
        var request = new MockHttpServletRequest("GET", "/missing");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/missing");

        var response = new ApiErrorController().error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().path()).isEqualTo("/missing");
    }

    @Test
    void errorHidesInternalMessageAndReturnsTraceIdForServerError() {
        var request = new MockHttpServletRequest("GET", "/failure");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "database details");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failure");
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("database details"));

        var response = new ApiErrorController().error(request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
        assertThat(response.getBody().message()).doesNotContain("database details");
        assertThat(response.getBody().traceId()).isNotBlank();
    }
}
