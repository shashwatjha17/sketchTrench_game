package com.sketchtrench.exception;

import org.springframework.http.HttpStatus;

/** Resource not found → HTTP 404. */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found: " + id);
    }
}
