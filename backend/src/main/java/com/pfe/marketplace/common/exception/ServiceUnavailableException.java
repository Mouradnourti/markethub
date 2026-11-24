package com.pfe.marketplace.common.exception;

import org.springframework.http.HttpStatus;

/** A dependency (AI service, invoice service…) is down; the client can retry later. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
