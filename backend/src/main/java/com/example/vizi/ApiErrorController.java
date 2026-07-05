package com.example.vizi;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        var status = statusCode(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        var message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        var path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        return ResponseEntity
                .status(status)
                .body(ApiError.of(status, value(message), value(path, request.getRequestURI())));
    }

    private int statusCode(Object value) {
        if (value instanceof Integer status) {
            return status;
        }
        if (value instanceof String status) {
            try {
                return Integer.parseInt(status);
            } catch (NumberFormatException ignored) {
                return 500;
            }
        }
        return 500;
    }

    private String value(Object value) {
        return value(value, "");
    }

    private String value(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }
}
