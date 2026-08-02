package com.sketchtrench.exception;

import org.springframework.http.HttpStatus;

/** Authenticated but not allowed to do this → HTTP 403. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
