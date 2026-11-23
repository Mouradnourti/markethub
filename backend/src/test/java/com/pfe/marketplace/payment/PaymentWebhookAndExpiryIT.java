package com.pfe.marketplace.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfe.marketplace.payment.service.UnpaidOrderExpiryJob;
import com.pfe.marketplace.support.ApiFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PaymentWebhookAndExpiryIT extends ApiFixtures {

    @Autowired UnpaidOrderExpiryJob expiryJob;

    @Test
    void webhookIsRefusedWhenNoSecretIsConfigured() throws Exception {
        mockMvc.perform(post("/api/payments/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "t=1,v1=fake").content("{}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void abandonedCardPaymentReleasesStock() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "Expiry " + System.nanoTime());
        String seller = approvedSeller(admin, "Expiry Shop");
        long product = createProduct(seller, category, "Kettle", "30.00", 3);
        String customer = register(uniqueEmail("abandon"), "CUSTOMER", null);
        addToCart(customer, product, 2);
        long orderId = checkout(customer, "CARD").get("order").get("id").asLong();
        assertThat(stockOf(product)).isEqualTo(1);

        jdbc.update("update shop_orders set created_at = now() - interval '2 hours' where order_id = ?", orderId);
        assertThat(expiryJob.expireUnpaidOrders()).isGreaterThanOrEqualTo(1);

        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select payment_status from orders where id = ?", String.class, orderId))
                .isEqualTo("FAILED");
    }

    @Test
    void invoiceRequiresTheNotificationServiceAndReturns503WhenItIsDown() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "Invoice " + System.nanoTime());
        String seller = approvedSeller(admin, "Invoice Shop");
        long product = createProduct(seller, category, "Mug", "8.00", 3);
        String customer = register(uniqueEmail("invoice"), "CUSTOMER", null);
        addToCart(customer, product, 1);
        long orderId = checkout(customer, "COD").get("order").get("id").asLong();

        perform(customer, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/orders/" + orderId + "/invoice"), null).andExpect(status().isServiceUnavailable());
    }
}
