package com.pfe.marketplace.payment.gateway;

import com.pfe.marketplace.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class PaymentProviderException extends ApiException {

    public PaymentProviderException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message);
        initCause(cause);
    }
}
