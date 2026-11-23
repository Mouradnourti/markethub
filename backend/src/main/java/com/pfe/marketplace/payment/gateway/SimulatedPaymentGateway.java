package com.pfe.marketplace.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;

/** Used when no Stripe key is configured: every payment succeeds unless a decline is simulated. */
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public String providerName() {
        return "STRIPE_MOCK";
    }

    @Override
    public Intent createIntent(String orderReference, BigDecimal amount, String currency) {
        String reference = "mock_pi_" + UUID.randomUUID().toString().replace("-", "");
        return new Intent(reference, reference + "_secret");
    }

    @Override
    public Outcome retrieveOutcome(String reference, boolean simulateDecline) {
        return simulateDecline ? Outcome.FAILED : Outcome.SUCCEEDED;
    }
}
