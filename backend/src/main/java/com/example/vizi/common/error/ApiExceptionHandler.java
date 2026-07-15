package com.example.vizi.common.error;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(400, "Validation failed", request.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformedJson(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(400, "Malformed JSON", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ApiError.of(400, exception.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        var status = exception.getStatusCode().value();
        return ResponseEntity
                .status(status)
                .body(ApiError.of(status, exception.getReason(), request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "Not Found", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        // Surface root cause class for faster local debugging (still 500).
        String detail = exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : (": " + exception.getMessage()));
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, detail, request.getRequestURI()));
    }
}
