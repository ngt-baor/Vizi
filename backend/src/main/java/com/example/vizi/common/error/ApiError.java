package com.example.vizi.common.error;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;

record ApiError(
        int status,
        String error,
        String message,
        String path,
        String time,
        String traceId
) {
    static ApiError of(int status, String message, String path) {
        return of(status, message, path, newTraceId());
    }

    static ApiError of(int status, String message, String path, String traceId) {
        var httpStatus = HttpStatus.resolve(status);
        var error = httpStatus != null ? httpStatus.getReasonPhrase() : "Unexpected Error";
        var safeMessage = message == null || message.isBlank() ? error : message;
        var safeTraceId = traceId == null || traceId.isBlank() ? newTraceId() : traceId;
        return new ApiError(status, error, safeMessage, path, Instant.now().toString(), safeTraceId);
    }

    static String newTraceId() {
        return UUID.randomUUID().toString();
    }
}
