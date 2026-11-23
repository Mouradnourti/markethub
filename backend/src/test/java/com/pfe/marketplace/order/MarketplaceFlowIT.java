package com.pfe.marketplace.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.support.ApiFixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Main business journeys through the HTTP API, on a real PostgreSQL database. */
class MarketplaceFlowIT extends ApiFixtures {

    private String admin;
    private long category;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        category = createCategory(admin, "Electronics " + System.nanoTime());
    }

    @Test
    void codOrderFromTwoShopsGoesThroughTheWholeLifecycle() throws Exception {
        String sellerA = approvedSeller(admin, "Shop A");
        String sellerB = approvedSeller(admin, "Shop B");
        long headphones = createProduct(sellerA, category, "Headphones", "59.90", 10);
        long cable = createProduct(sellerB, category, "Cable", "9.50", 3);
        String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);

        addToCart(customer, headphones, 2);
        addToCart(customer, cable, 1);
        JsonNode cart = send(customer, get("/api/cart"), null, 200);
        assertThat(cart.get("itemCount").asInt()).isEqualTo(3);
        assertThat(cart.get("shippingFee").decimalValue()).isEqualByComparingTo("0.00"); // above free threshold

        JsonNode order = checkout(customer, "COD").get("order");
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("129.30");
        assertThat(order.get("shopOrders")).hasSize(2);
        assertThat(order.get("paymentStatus").asText()).isEqualTo("PENDING");
        assertThat(stockOf(headphones)).isEqualTo(8);
        assertThat(send(customer, get("/api/cart"), null, 200).get("itemCount").asInt()).isZero();

        // Seller A sees only its own parcel, with commission computed from the 10 % default.
        JsonNode inbox = send(sellerA, get("/api/seller/orders"), null, 200).get("content");
        assertThat(inbox).hasSize(1);
        long parcelA = inbox.get(0).get("id").asLong();
        assertThat(inbox.get(0).get("commissionAmount").decimalValue()).isEqualByComparingTo("11.98");

        advance(sellerA, parcelA, "CONFIRMED", null);
        advance(sellerA, parcelA, "SHIPPED", "TRK-001");
        JsonNode delivered = advance(sellerA, parcelA, "DELIVERED", null);
        assertThat(delivered.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(delivered.get("history")).hasSize(4);

        // Illegal transition is rejected.
        perform(sellerA, patch("/api/seller/orders/" + parcelA + "/status"), Map.of("status", "PENDING"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnprocessableEntity());

        // Seller B's customer refused the COD parcel at the door.
        long parcelB = send(sellerB, get("/api/seller/orders"), null, 200).get("content").get(0).get("id").asLong();
        advance(sellerB, parcelB, "CONFIRMED", null);
        advance(sellerB, parcelB, "SHIPPED", null);
        advance(sellerB, parcelB, "RETURNED", "Customer refused the parcel");
        assertThat(stockOf(cable)).isEqualTo(3);

        // Review only after delivery, once.
        JsonNode eligibility = send(customer, get("/api/products/" + headphones + "/reviews/eligibility"), null, 200);
        assertThat(eligibility.get("canReview").asBoolean()).isTrue();
        send(customer, post("/api/products/" + headphones + "/reviews"), Map.of("rating", 4, "comment", "Great sound"), 201);
        send(customer, post("/api/products/" + headphones + "/reviews"), Map.of("rating", 5), 409);
        JsonNode product = send(null, get("/api/products/" + headphones), null, 200);
        assertThat(product.get("avgRating").decimalValue()).isEqualByComparingTo("4.00");
        assertThat(product.get("reviewCount").asInt()).isEqualTo(1);
        send(customer, post("/api/products/" + cable + "/reviews"), Map.of("rating", 1), 422);

        // Return request, approved by the admin: stock goes back.
        long customerOrder = order.get("id").asLong();
        send(customer, post("/api/orders/shop-orders/" + parcelA + "/return-request"),
                Map.of("type", "RETURN", "reason", "Left earcup crackles"), 201);
        JsonNode reloaded = send(customer, get("/api/orders/" + customerOrder), null, 200);
        assertThat(reloaded.get("shopOrders").get(0).get("returnRequested").asBoolean()).isTrue();
        JsonNode disputes = send(admin, get("/api/admin/disputes?status=OPEN"), null, 200).get("content");
        long disputeId = disputes.get(0).get("id").asLong();
        send(admin, patch("/api/admin/disputes/" + disputeId), Map.of("decision", "APPROVED", "note", "Refund"), 200);
        assertThat(stockOf(headphones)).isEqualTo(10);

        JsonNode sellerStats = send(sellerA, get("/api/seller/stats"), null, 200);
        assertThat(sellerStats.get("returnedOrders").asLong()).isEqualTo(1);
        assertThat(sellerStats.get("monthly")).hasSize(12);
        JsonNode adminStats = send(admin, get("/api/admin/stats"), null, 200);
        assertThat(adminStats.get("totalOrders").asLong()).isPositive();
    }

    @Test
    void cardOrderIsPaidThroughTheSimulatorAndDeclinedCardReleasesStock() throws Exception {
        String seller = approvedSeller(admin, "Card Shop");
        long lamp = createProduct(seller, category, "Lamp", "20.00", 5);
        String customer = register(uniqueEmail("card"), "CUSTOMER", null);

        addToCart(customer, lamp, 1);
        JsonNode response = checkout(customer, "CARD");
        assertThat(response.get("payment").get("provider").asText()).isEqualTo("STRIPE_MOCK");
        assertThat(response.get("order").get("total").decimalValue()).isEqualByComparingTo("24.99"); // + flat fee
        long paidOrder = response.get("order").get("id").asLong();
        assertThat(send(seller, get("/api/seller/orders?status=AWAITING_PAYMENT"), null, 200).get("content")).hasSize(1);

        JsonNode paid = send(customer, post("/api/payments/" + paidOrder + "/confirm"), Map.of("simulateDecline", false), 200);
        assertThat(paid.get("paymentStatus").asText()).isEqualTo("PAID");
        assertThat(send(seller, get("/api/seller/orders?status=PENDING"), null, 200).get("content")).hasSize(1);

        addToCart(customer, lamp, 2);
        long declinedOrder = checkout(customer, "CARD").get("order").get("id").asLong();
        assertThat(stockOf(lamp)).isEqualTo(2);
        JsonNode declined = send(customer, post("/api/payments/" + declinedOrder + "/confirm"),
                Map.of("simulateDecline", true), 200);
        assertThat(declined.get("paymentStatus").asText()).isEqualTo("FAILED");
        assertThat(stockOf(lamp)).isEqualTo(4);
    }

    @Test
    void stockAndCartRulesAreEnforced() throws Exception {
        String seller = approvedSeller(admin, "Stock Shop");
        long mug = createProduct(seller, category, "Mug", "12.00", 2);
        String customer = register(uniqueEmail("stock"), "CUSTOMER", null);

        send(customer, post("/api/cart/items"), Map.of("productId", mug, "quantity", 3), 422);
        addToCart(customer, mug, 2);
        send(customer, put("/api/cart/items/" + mug), Map.of("quantity", 5), 422);

        // Stock disappears between cart and checkout: the order is refused and nothing is reserved.
        send(seller, patch("/api/seller/products/" + mug + "/stock"), Map.of("stock", 1), 200);
        send(customer, post("/api/orders/checkout"), Map.of("paymentMethod", "COD", "shippingAddress", Map.of(
                "fullName", "A B", "phone", "0612345678", "addressLine", "1 rue", "city", "Paris",
                "postalCode", "75001", "country", "France")), 422);
        assertThat(stockOf(mug)).isEqualTo(1);

        send(customer, put("/api/cart/items/" + mug), Map.of("quantity", 1), 200);
        long orderId = checkout(customer, "COD").get("order").get("id").asLong();
        assertThat(stockOf(mug)).isZero();

        // The customer cancels before the seller confirms: stock is released.
        JsonNode cancelled = send(customer, post("/api/orders/" + orderId + "/cancel"), Map.of("reason", "Changed my mind"), 200);
        assertThat(cancelled.get("shopOrders").get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("paymentStatus").asText()).isEqualTo("FAILED");
        assertThat(stockOf(mug)).isEqualTo(1);
        send(customer, post("/api/orders/checkout"), Map.of("paymentMethod", "COD", "shippingAddress", Map.of(
                "fullName", "A B", "phone", "0612345678", "addressLine", "1 rue", "city", "Paris",
                "postalCode", "75001", "country", "France")), 422); // empty cart
    }

    @Test
    void pendingShopCannotSellAndSuspendedShopDisappearsFromCatalog() throws Exception {
        String pendingSeller = register(uniqueEmail("pending"), "SELLER", "Pending Shop");
        send(pendingSeller, post("/api/seller/products"), Map.of("name", "X", "price", "1.00", "stock", 1,
                "categoryId", category), 403);

        String seller = approvedSeller(admin, "Soon Suspended " + System.nanoTime());
        long vase = createProduct(seller, category, "Vase", "30.00", 4);
        JsonNode search = send(null, get("/api/products?categoryId=" + category + "&q=vase"), null, 200);
        assertThat(search.get("totalElements").asLong()).isEqualTo(1);

        long shopId = send(seller, get("/api/seller/shop"), null, 200).get("id").asLong();
        send(admin, patch("/api/admin/shops/" + shopId + "/status"), Map.of("status", "SUSPENDED"), 200);
        assertThat(send(null, get("/api/products?categoryId=" + category), null, 200).get("totalElements").asLong()).isZero();
        send(null, get("/api/products/" + vase), null, 404);
    }

    @Test
    void catalogFiltersSortAndPaginate() throws Exception {
        String seller = approvedSeller(admin, "Filter Shop");
        createProduct(seller, category, "Alpha Speaker", "10.00", 5);
        createProduct(seller, category, "Beta Speaker", "50.00", 5);
        createProduct(seller, category, "Gamma Speaker", "90.00", 0);

        JsonNode cheapFirst = send(null, get("/api/products?categoryId=" + category + "&sort=PRICE_ASC&size=2"), null, 200);
        assertThat(cheapFirst.get("totalElements").asLong()).isEqualTo(3);
        assertThat(cheapFirst.get("totalPages").asInt()).isEqualTo(2);
        assertThat(cheapFirst.get("content").get(0).get("name").asText()).isEqualTo("Alpha Speaker");

        JsonNode ranged = send(null, get("/api/products?categoryId=" + category + "&minPrice=20&maxPrice=95&inStock=true"),
                null, 200);
        assertThat(ranged.get("totalElements").asLong()).isEqualTo(1);
        assertThat(ranged.get("content").get(0).get("name").asText()).isEqualTo("Beta Speaker");

        assertThat(send(null, get("/api/categories"), null, 200).size()).isPositive();
        assertThat(send(null, get("/api/config"), null, 200).get("stripeEnabled").asBoolean()).isFalse();
    }

    @Test
    void adminManagesUsersCommissionsAndSettings() throws Exception {
        String seller = approvedSeller(admin, "Commission Shop");
        long shopId = send(seller, get("/api/seller/shop"), null, 200).get("id").asLong();
        send(admin, patch("/api/admin/shops/" + shopId + "/commission"), Map.of("commissionRate", 5), 200);
        long product = createProduct(seller, category, "Book", "100.00", 3);

        String customerEmail = uniqueEmail("managed");
        String customer = register(customerEmail, "CUSTOMER", null);
        addToCart(customer, product, 1);
        checkout(customer, "COD");
        JsonNode parcel = send(seller, get("/api/seller/orders"), null, 200).get("content").get(0);
        assertThat(parcel.get("commissionAmount").decimalValue()).isEqualByComparingTo("5.00");

        JsonNode settings = send(admin, get("/api/admin/settings"), null, 200);
        assertThat(settings.get("defaultCommissionRate").decimalValue()).isEqualByComparingTo("10.00");
        send(admin, put("/api/admin/settings"), Map.of("defaultCommissionRate", 12, "shippingFlatFee", 4.99,
                "freeShippingThreshold", 50), 200);
        send(admin, put("/api/admin/settings"), Map.of("defaultCommissionRate", 10, "shippingFlatFee", 4.99,
                "freeShippingThreshold", 50), 200);

        long customerId = send(admin, get("/api/admin/users?q=" + customerEmail.substring(0, 12)), null, 200)
                .get("content").get(0).get("id").asLong();
        send(admin, patch("/api/admin/users/" + customerId + "/enabled"), Map.of("enabled", false), 200);
        perform(null, post("/api/auth/login"), Map.of("email", customerEmail, "password", "Password123"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        assertThat(send(admin, get("/api/admin/shops?status=APPROVED"), null, 200).get("totalElements").asLong()).isPositive();
    }

    private JsonNode advance(String seller, long parcelId, String status, String tracking) throws Exception {
        Map<String, Object> body = tracking == null ? Map.of("status", status)
                : Map.of("status", status, "trackingNumber", tracking);
        return send(seller, patch("/api/seller/orders/" + parcelId + "/status"), body, 200);
    }
}
