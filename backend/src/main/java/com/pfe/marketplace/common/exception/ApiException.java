package com.pfe.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/** Base class of every business error; {@link GlobalExceptionHandler} turns it into a ProblemDetail. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
