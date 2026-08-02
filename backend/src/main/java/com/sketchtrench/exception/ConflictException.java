package com.sketchtrench.exception;

import org.springframework.http.HttpStatus;

/** Request conflicts with current state (duplicate email, room full...) → HTTP 409. */
public class ConflictException extends ApiException {

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
