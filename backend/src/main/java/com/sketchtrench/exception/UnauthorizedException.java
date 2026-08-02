package com.sketchtrench.exception;

import org.springframework.http.HttpStatus;

/** Authentication is missing or invalid → HTTP 401. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
