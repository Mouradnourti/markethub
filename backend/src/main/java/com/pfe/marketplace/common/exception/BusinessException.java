package com.pfe.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/** A request that is well-formed but breaks a business rule (422). */
public class BusinessException extends ApiException {

    public BusinessException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
