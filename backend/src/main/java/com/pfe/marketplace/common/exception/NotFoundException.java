package com.pfe.marketplace.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "%s %s not found".formatted(resource, id));
    }

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
