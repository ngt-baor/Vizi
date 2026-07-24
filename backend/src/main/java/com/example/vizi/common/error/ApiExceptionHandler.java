package com.example.vizi.common.error;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final String INTERNAL_ERROR_MESSAGE = "Internal server error";
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Validation failed", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Malformed JSON", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        var status = exception.getStatusCode().value();
        var traceId = ApiError.newTraceId();
        if (status >= 500 && !(exception.getCause() instanceof IllegalArgumentException)) {
            logServerError(traceId, request, exception);
            return response(status, INTERNAL_ERROR_MESSAGE, request, traceId);
        }
        return response(status, exception.getReason(), request, traceId);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Not Found", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        var traceId = ApiError.newTraceId();
        logServerError(traceId, request, exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE, request, traceId);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        return response(status, message, request, ApiError.newTraceId());
    }

    private ResponseEntity<ApiError> response(int status, String message, HttpServletRequest request, String traceId) {
        return ResponseEntity
                .status(status)
                .body(ApiError.of(status, message, request.getRequestURI(), traceId));
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request, String traceId) {
        return response(status.value(), message, request, traceId);
    }

    private void logServerError(String traceId, HttpServletRequest request, Exception exception) {
        logger.error(
                "Unhandled server exception traceId={} method={} path={} type={} message={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getName(),
                exception.getMessage(),
                exception
        );
    }
}
