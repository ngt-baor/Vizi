package com.example.vizi.common.error;

import java.time.Instant;

import org.springframework.http.HttpStatus;

record ApiError(
        int status,
        String error,
        String message,
        String path,
        String time
) {
    static ApiError of(int status, String message, String path) {
        var httpStatus = HttpStatus.resolve(status);
        var error = httpStatus != null ? httpStatus.getReasonPhrase() : "Unexpected Error";
        var safeMessage = message == null || message.isBlank() ? error : message;
        return new ApiError(status, error, safeMessage, path, Instant.now().toString());
    }
}
