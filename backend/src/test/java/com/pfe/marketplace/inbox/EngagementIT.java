package com.pfe.marketplace.inbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.cart.service.CartReminderJob;
import com.pfe.marketplace.inbox.service.LiveEventHub;
import com.pfe.marketplace.support.ApiFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Wishlist alerts, the notification bell, the live event stream and abandoned-cart reminders. */
class EngagementIT extends ApiFixtures {

    @Autowired
    CartReminderJob cartReminderJob;

    @Autowired
    LiveEventHub hub;

    private String admin;
    private String seller;
    private long category;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        seller = approvedSeller(admin, "Engagement Shop " + System.nanoTime());
        category = createCategory(admin, "Engagement " + System.nanoTime());
    }

    @Test
    void wishlistCustomersHearAboutPriceDropsAndRestocksOnce() throws Exception {
        long lamp = createProduct(seller, category, "Desk Lamp", "50.00", 0);
        String customer = register(uniqueEmail("fan"), "CUSTOMER", null);

        send(customer, put("/api/wishlist/" + lamp), null, 204);
        send(customer, put("/api/wishlist/" + lamp), null, 204);
        assertThat(send(customer, get("/api/wishlist/ids"), null, 200)).extracting(JsonNode::asLong).containsExactly(lamp);
        JsonNode saved = send(customer, get("/api/wishlist"), null, 200);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).get("priceWhenAdded").decimalValue()).isEqualByComparingTo("50.00");

        send(seller, patch("/api/seller/products/" + lamp + "/stock"), Map.of("stock", 8), 200);
        JsonNode restock = awaitNotifications(customer, 1).get(0);
        assertThat(restock.get("type").asText()).isEqualTo("BACK_IN_STOCK");
        assertThat(restock.get("link").asText()).isEqualTo("/products/" + lamp);

        send(seller, put("/api/seller/products/" + lamp), productBody("Desk Lamp", "40.00", 8), 200);
        JsonNode drop = awaitNotifications(customer, 2).get(0);
        assertThat(drop.get("type").asText()).isEqualTo("PRICE_DROP");
        assertThat(drop.get("body").asText()).contains("€40.00").contains("€50.00");

        // Going back up and down to a price already announced does not alert again.
        send(seller, put("/api/seller/products/" + lamp), productBody("Desk Lamp", "45.00", 8), 200);
        send(seller, put("/api/seller/products/" + lamp), productBody("Desk Lamp", "42.00", 8), 200);
        Thread.sleep(400);
        assertThat(notifications(customer)).hasSize(2);

        send(customer, delete("/api/wishlist/" + lamp), null, 204);
        assertThat(send(customer, get("/api/wishlist"), null, 200)).isEmpty();
    }

    @Test
    void sellersAreToldAboutNewOrdersAndCustomersAboutTheirParcels() throws Exception {
        long mug = createProduct(seller, category, "Mug", "12.00", 10);
        String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(customer, mug, 2);
        JsonNode order = checkout(customer, "COD").get("order");

        JsonNode newOrder = awaitNotifications(seller, 1).get(0);
        assertThat(newOrder.get("type").asText()).isEqualTo("NEW_ORDER");
        assertThat(newOrder.get("title").asText()).contains(order.get("reference").asText());
        assertThat(newOrder.get("body").asText()).startsWith("2 items");

        long parcel = order.get("shopOrders").get(0).get("id").asLong();
        send(seller, patch("/api/seller/orders/" + parcel + "/status"), Map.of("status", "CONFIRMED"), 200);
        send(seller, patch("/api/seller/orders/" + parcel + "/status"),
                Map.of("status", "SHIPPED", "trackingNumber", "TRK-42"), 200);
        JsonNode shipped = awaitNotifications(customer, 1).get(0);
        assertThat(shipped.get("title").asText()).contains("on its way");
        assertThat(shipped.get("body").asText()).contains("TRK-42");
        assertThat(shipped.get("link").asText()).isEqualTo("/orders/" + order.get("id").asLong());

        assertThat(send(customer, get("/api/notifications/unread-count"), null, 200).get("unread").asLong()).isEqualTo(1);
        send(customer, post("/api/notifications/" + shipped.get("id").asLong() + "/read"), null, 200);
        assertThat(send(customer, get("/api/notifications/unread-count"), null, 200).get("unread").asLong()).isZero();
        send(seller, post("/api/notifications/" + shipped.get("id").asLong() + "/read"), null, 404);
        send(seller, post("/api/notifications/read-all"), null, 204);
        assertThat(send(seller, get("/api/notifications/unread-count"), null, 200).get("unread").asLong()).isZero();
    }

    @Test
    void theLiveStreamOpensOnlyWithAFreshSingleUseTicket() throws Exception {
        String email = uniqueEmail("live");
        String customer = register(email, "CUSTOMER", null);
        String ticket = send(customer, post("/api/notifications/stream-ticket"), null, 200).get("ticket").asText();
        long userId = jdbc.queryForObject("select id from users where email = ?", Long.class, email);

        mockMvc.perform(get("/api/notifications/stream").param("ticket", ticket))
                .andExpect(request().asyncStarted());
        assertThat(hub.connectionCount(userId)).isEqualTo(1);

        mockMvc.perform(get("/api/notifications/stream").param("ticket", ticket)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/stream").param("ticket", "forged")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAbandonedCartGetsOneReminderUntilSomethingNewIsAdded() throws Exception {
        long sofa = createProduct(seller, category, "Sofa", "300.00", 5);
        String email = uniqueEmail("away");
        String customer = register(email, "CUSTOMER", null);
        addToCart(customer, sofa, 1);
        jdbc.update("update cart_items set added_at = now() - interval '2 days' where user_id = (select id from users where email = ?)",
                email);

        assertThat(cartReminderJob.remindAbandonedCarts()).isGreaterThanOrEqualTo(1);
        List<JsonNode> sent = notifications(customer);
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).get("type").asText()).isEqualTo("CART_REMINDER");
        assertThat(sent.get(0).get("body").asText()).contains("€300.00");

        cartReminderJob.remindAbandonedCarts();
        assertThat(notifications(customer)).hasSize(1);
    }

    private Map<String, Object> productBody(String name, String price, int stock) {
        return Map.of("name", name, "description", name + " description", "price", price, "stock", stock,
                "categoryId", category, "imageUrls", List.of("/uploads/demo/lamp.svg"));
    }

    private List<JsonNode> notifications(String token) throws Exception {
        List<JsonNode> result = new java.util.ArrayList<>();
        send(token, get("/api/notifications"), null, 200).get("content").forEach(result::add);
        return result;
    }

    /** Notifications are created on a background thread after commit, so wait for them. */
    private List<JsonNode> awaitNotifications(String token, int count) throws Exception {
        for (int i = 0; i < 50; i++) {
            List<JsonNode> found = notifications(token);
            if (found.size() >= count) {
                return found;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Expected " + count + " notification(s), got " + notifications(token).size());
    }
}
