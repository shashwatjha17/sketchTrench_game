package com.sketchtrench.exception;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * The JSON shape every error response has, so clients can parse one contract.
 * A record: immutable by design, and the canonical constructor (with the field-errors
 * map) is the single place the shape is defined.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ErrorResponse of(HttpStatus status, String errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), status.value(), errorCode, message, path, Map.of());
    }

    public static ErrorResponse of(HttpStatus status, String errorCode, String message, String path,
                                   Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status.value(), errorCode, message, path, fieldErrors);
    }
}
