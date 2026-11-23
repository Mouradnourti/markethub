package com.pfe.marketplace.payment.controller;

import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.payment.gateway.PaymentGateway;
import com.pfe.marketplace.payment.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Stripe → backend callbacks. The signature is verified with STRIPE_WEBHOOK_SECRET before anything is trusted. */
@Slf4j
@Tag(name = "Payments")
@RestController
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentService paymentService;
    private final AppProperties properties;

    @Operation(summary = "Stripe webhook (payment_intent.succeeded / payment_intent.payment_failed)")
    @PostMapping("/api/payments/stripe/webhook")
    public ResponseEntity<Void> handle(@RequestBody String payload,
                                       @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        String secret = properties.stripe() == null ? null : properties.stripe().webhookSecret();
        if (secret == null || secret.isBlank() || signature == null) {
            return ResponseEntity.status(503).build();
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with an invalid signature");
            return ResponseEntity.badRequest().build();
        }
        PaymentGateway.Outcome outcome = switch (event.getType()) {
            case "payment_intent.succeeded" -> PaymentGateway.Outcome.SUCCEEDED;
            case "payment_intent.payment_failed" -> PaymentGateway.Outcome.FAILED;
            default -> null;
        };
        StripeObject object = event.getDataObjectDeserializer().getObject().orElse(null);
        if (outcome != null && object instanceof PaymentIntent intent) {
            paymentService.handleProviderEvent(intent.getId(), outcome);
        }
        return ResponseEntity.ok().build();
    }
}
