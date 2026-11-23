package com.pfe.marketplace.payment.gateway;

import com.pfe.marketplace.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class PaymentGatewayConfig {

    @Bean
    PaymentGateway paymentGateway(AppProperties properties) {
        AppProperties.Stripe stripe = properties.stripe();
        if (stripe != null && stripe.enabled()) {
            log.info("Card payments: Stripe (test mode)");
            return new StripePaymentGateway(stripe.secretKey());
        }
        log.info("Card payments: simulator (set STRIPE_SECRET_KEY to use Stripe test mode)");
        return new SimulatedPaymentGateway();
    }
}
