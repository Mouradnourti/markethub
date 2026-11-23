package com.pfe.marketplace.payment.gateway;

import java.math.BigDecimal;

/** Abstraction over the card payment provider (real Stripe or the offline simulator). */
public interface PaymentGateway {

    record Intent(String reference, String clientSecret) {
    }

    enum Outcome { SUCCEEDED, FAILED, PENDING }

    String providerName();

    Intent createIntent(String orderReference, BigDecimal amount, String currency);

    /**
     * Current state of the intent. {@code simulateDecline} is only honoured by the simulator so the demo can show a
     * failed payment.
     */
    Outcome retrieveOutcome(String reference, boolean simulateDecline);
}
