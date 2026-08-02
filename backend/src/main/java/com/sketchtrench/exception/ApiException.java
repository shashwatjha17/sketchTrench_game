package com.sketchtrench.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for all expected, business-level errors. Being a {@link RuntimeException}
 * means it needs no checked-exception ceremony, but unlike throwing a raw
 * {@link RuntimeException} it carries a status + stable code so the
 * {@link GlobalExceptionHandler} can map it to a precise HTTP response.
 *
 * <p>Why a hierarchy instead of a catch-all? Controllers/services stay readable
 * ("throw new NotFoundException(...)") and the handler stays one method, while each
 * subclass owns its HTTP semantics.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
}
