package com.pfe.marketplace.payment.gateway;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.util.Locale;

/** Stripe Payment Intents (use test keys: sk_test_… / pk_test_…). Amounts are sent in minor units. */
public class StripePaymentGateway implements PaymentGateway {

    private final StripeClient client;

    public StripePaymentGateway(String secretKey) {
        this.client = new StripeClient(secretKey);
    }

    @Override
    public String providerName() {
        return "STRIPE";
    }

    @Override
    public Intent createIntent(String orderReference, BigDecimal amount, String currency) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.movePointRight(2).longValueExact())
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .setDescription("MarketHub order " + orderReference)
                .putMetadata("orderReference", orderReference)
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .build();
        try {
            PaymentIntent intent = client.paymentIntents().create(params);
            return new Intent(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new PaymentProviderException("Stripe refused to create the payment", e);
        }
    }

    @Override
    public Outcome retrieveOutcome(String reference, boolean simulateDecline) {
        try {
            return switch (client.paymentIntents().retrieve(reference).getStatus()) {
                case "succeeded" -> Outcome.SUCCEEDED;
                case "canceled", "requires_payment_method" -> Outcome.FAILED;
                default -> Outcome.PENDING;
            };
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not read the payment status from Stripe", e);
        }
    }
}
