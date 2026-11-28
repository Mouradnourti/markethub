package com.pfe.marketplace.promotion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.support.ApiFixtures;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Seller discount codes: preview in the cart, discount at checkout, limits, and release on cancellation. */
class CouponIT extends ApiFixtures {

    private String admin;
    private String sellerA;
    private String sellerB;
    private long shoeA;
    private long hatB;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long category = createCategory(admin, "Coupons " + System.nanoTime());
        sellerA = approvedSeller(admin, "Coupon Shop A " + System.nanoTime());
        sellerB = approvedSeller(admin, "Coupon Shop B " + System.nanoTime());
        shoeA = createProduct(sellerA, category, "Runner", "80.00", 20);
        hatB = createProduct(sellerB, category, "Cap", "20.00", 20);
    }

    @Test
    void codeDiscountsOnlyItsShopAndCommissionFollowsTheNetPrice() throws Exception {
        String code = uniqueCode("SPRING");
        JsonNode coupon = send(sellerA, post("/api/seller/coupons"),
                coupon(code.toLowerCase(), "PERCENT", "25", null), 201);
        assertThat(coupon.get("code").asText()).isEqualTo(code);
        assertThat(coupon.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(coupon.get("label").asText()).isEqualTo("25% off");

        String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(customer, shoeA, 1);
        addToCart(customer, hatB, 1);

        JsonNode cart = send(customer, get("/api/cart").param("coupon", code), null, 200);
        assertThat(cart.get("discount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(cart.get("coupon").get("code").asText()).isEqualTo(code);
        assertThat(cart.get("total").decimalValue())
                .isEqualByComparingTo(cart.get("subtotal").decimalValue().subtract(new java.math.BigDecimal("20.00"))
                        .add(cart.get("shippingFee").decimalValue()));

        JsonNode order = checkoutWith(customer, code, 201);
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(order.get("discountTotal").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(order.get("total").decimalValue())
                .isEqualByComparingTo(new java.math.BigDecimal("80.00").add(order.get("shippingFee").decimalValue()));
        JsonNode parcelA = parcelOf(order, "Runner");
        assertThat(parcelA.get("couponCode").asText()).isEqualTo(code);
        assertThat(parcelA.get("discountAmount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(parcelA.get("subtotal").decimalValue()).isEqualByComparingTo("60.00");

        JsonNode inbox = send(sellerA, get("/api/seller/orders"), null, 200).get("content");
        assertThat(inbox.get(0).get("commissionAmount").decimalValue()).isEqualByComparingTo("6.00");
        assertThat(inbox.get(0).get("netAmount").decimalValue()).isEqualByComparingTo("54.00");
        assertThat(send(sellerA, get("/api/seller/coupons"), null, 200).get(0).get("usedCount").asInt()).isEqualTo(1);
    }

    @Test
    void refusalsExplainWhatIsWrong() throws Exception {
        String minimum = uniqueCode("BIG");
        Map<String, Object> body = coupon(minimum, "FIXED", "10", "150");
        send(sellerA, post("/api/seller/coupons"), body, 201);
        String expired = uniqueCode("OLD");
        Map<String, Object> old = coupon(expired, "FIXED", "5", null);
        old.put("startsAt", Instant.now().minus(10, ChronoUnit.DAYS).toString());
        old.put("endsAt", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        send(sellerA, post("/api/seller/coupons"), old, 201);
        String otherShop = uniqueCode("HATS");
        send(sellerB, post("/api/seller/coupons"), coupon(otherShop, "PERCENT", "10", null), 201);

        String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(customer, shoeA, 1);
        assertThat(cartError(customer, "NOPE-" + System.nanoTime())).contains("does not exist");
        assertThat(cartError(customer, minimum)).contains("Spend at least €150.00");
        assertThat(cartError(customer, expired)).contains("has expired");
        assertThat(cartError(customer, otherShop)).contains("only applies to products sold by");

        // Codes are unique across shops and percentages are capped.
        send(sellerB, post("/api/seller/coupons"), coupon(minimum, "FIXED", "5", null), 409);
        send(sellerA, post("/api/seller/coupons"), coupon(uniqueCode("HUGE"), "PERCENT", "95", null), 422);
        send(sellerA, post("/api/seller/coupons"), coupon("x", "PERCENT", "10", null), 400);
    }

    @Test
    void oncePerCustomerAndUsageLimitAreEnforcedAndCancellationGivesTheUseBack() throws Exception {
        String code = uniqueCode("ONCE");
        Map<String, Object> body = coupon(code, "FIXED", "5", null);
        body.put("maxUses", 1);
        long couponId = send(sellerA, post("/api/seller/coupons"), body, 201).get("id").asLong();

        String first = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(first, shoeA, 1);
        long orderId = checkoutWith(first, code, 201).get("id").asLong();

        String second = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(second, shoeA, 1);
        assertThat(cartError(second, code)).contains("usage limit");

        addToCart(first, shoeA, 1);
        assertThat(cartError(first, code)).containsAnyOf("already used", "usage limit");

        send(first, post("/api/orders/" + orderId + "/cancel"), Map.of("reason", "Changed my mind"), 200);
        assertThat(jdbc.queryForObject("select used_count from coupons where id = ?", Integer.class, couponId)).isZero();
        assertThat(send(second, get("/api/cart").param("coupon", code), null, 200).get("discount").decimalValue())
                .isEqualByComparingTo("5.00");
    }

    @Test
    void sellersManageOnlyTheirOwnCodesAndFeaturedOnesAreAdvertised() throws Exception {
        String code = uniqueCode("HELLO");
        Map<String, Object> body = coupon(code, "PERCENT", "10", "30");
        body.put("featured", true);
        long id = send(sellerA, post("/api/seller/coupons"), body, 201).get("id").asLong();
        long shopA = jdbc.queryForObject("select shop_id from coupons where id = ?", Long.class, id);

        JsonNode offers = send(null, get("/api/shops/" + shopA + "/offers"), null, 200);
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).get("label").asText()).isEqualTo("10% off");

        body.put("active", false);
        assertThat(send(sellerA, put("/api/seller/coupons/" + id), body, 200).get("status").asText()).isEqualTo("PAUSED");
        assertThat(send(null, get("/api/shops/" + shopA + "/offers"), null, 200)).isEmpty();

        send(sellerB, put("/api/seller/coupons/" + id), body, 404);
        send(sellerB, delete("/api/seller/coupons/" + id), null, 404);
        send(sellerA, delete("/api/seller/coupons/" + id), null, 204);
    }

    private JsonNode checkoutWith(String customer, String code, int status) throws Exception {
        Map<String, Object> address = Map.of("fullName", "Sara Customer", "phone", "+33 6 12 34 56 78",
                "addressLine", "12 rue des Lilas", "city", "Lyon", "postalCode", "69001", "country", "France");
        return send(customer, post("/api/orders/checkout"),
                Map.of("paymentMethod", "COD", "shippingAddress", address, "couponCode", code), status).get("order");
    }

    private String cartError(String customer, String code) throws Exception {
        return send(customer, get("/api/cart").param("coupon", code), null, 422).get("detail").asText();
    }

    private static JsonNode parcelOf(JsonNode order, String productName) {
        for (JsonNode parcel : order.get("shopOrders")) {
            if (parcel.get("items").get(0).get("productName").asText().equals(productName)) {
                return parcel;
            }
        }
        throw new AssertionError("No parcel with " + productName);
    }

    private static Map<String, Object> coupon(String code, String type, String value, String minSubtotal) {
        Map<String, Object> body = new HashMap<>(Map.of("code", code, "type", type, "value", value));
        if (minSubtotal != null) {
            body.put("minSubtotal", minSubtotal);
        }
        return body;
    }

    private static String uniqueCode(String prefix) {
        String suffix = Long.toString(System.nanoTime(), 36).toUpperCase();
        return prefix + suffix.substring(suffix.length() - 6);
    }
}
