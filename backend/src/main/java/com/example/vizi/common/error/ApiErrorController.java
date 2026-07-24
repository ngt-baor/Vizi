package com.example.vizi.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ApiErrorController implements ErrorController {

    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final Logger logger = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping("/error")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        var status = statusCode(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        var message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        var path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        var traceId = ApiError.newTraceId();
        if (status >= 500) {
            logServerError(traceId, request);
        }
        var clientMessage = status >= 500 ? INTERNAL_ERROR_MESSAGE : value(message);

        return ResponseEntity
                .status(status)
                .body(ApiError.of(status, clientMessage, value(path, request.getRequestURI()), traceId));
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

    private void logServerError(String traceId, HttpServletRequest request) {
        var error = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (error instanceof Throwable exception) {
            logger.error(
                    "Unhandled server error traceId={} method={} path={} type={} message={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getClass().getName(),
                    exception.getMessage(),
                    exception
            );
            return;
        }
        logger.error(
                "Unhandled server error traceId={} method={} path={} status={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                statusCode(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE))
        );
    }
}
