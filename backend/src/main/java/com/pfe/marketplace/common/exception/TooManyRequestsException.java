package com.pfe.marketplace.common.exception;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Rate limit or lockout (429); {@link #getRetryAfter()} feeds the Retry-After header. */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
