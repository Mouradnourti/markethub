package com.pfe.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.support.ApiFixtures;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Product page content (sale price, tagline, highlights, specs), deals filter and "frequently bought together". */
class MerchandisingIT extends ApiFixtures {

    private String admin;
    private String seller;
    private long category;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        seller = approvedSeller(admin, "Merch Shop " + System.nanoTime());
        category = createCategory(admin, "Merch " + System.nanoTime());
    }

    @Test
    void sellerPublishesRichProductContentAndDealsAreFilterable() throws Exception {
        Map<String, Object> body = productBody("Trail Jacket", "79.00");
        body.put("compareAtPrice", "99.00");
        body.put("tagline", "Stay dry on every trail.");
        body.put("highlights", List.of("Waterproof", "  Packable  "));
        body.put("specs", List.of(Map.of("label", "Weight", "value", "320 g")));
        long onSale = send(seller, post("/api/seller/products"), body, 201).get("id").asLong();
        long fullPrice = createProduct(seller, category, "Trail Cap", "19.00", 5);

        JsonNode product = send(null, get("/api/products/" + onSale), null, 200);
        assertThat(product.get("compareAtPrice").decimalValue()).isEqualByComparingTo("99.00");
        assertThat(product.get("tagline").asText()).isEqualTo("Stay dry on every trail.");
        assertThat(product.get("highlights")).extracting(JsonNode::asText).containsExactly("Waterproof", "Packable");
        assertThat(product.get("specs").get(0).get("label").asText()).isEqualTo("Weight");
        assertThat(product.get("specs").get(0).get("value").asText()).isEqualTo("320 g");

        JsonNode deals = send(null, get("/api/products").param("categoryId", String.valueOf(category))
                .param("onSale", "true"), null, 200);
        assertThat(deals.get("content")).extracting(p -> p.get("id").asLong()).containsExactly(onSale);
        assertThat(deals.get("content").get(0).get("compareAtPrice").decimalValue()).isEqualByComparingTo("99.00");
        JsonNode all = send(null, get("/api/products").param("categoryId", String.valueOf(category)), null, 200);
        assertThat(all.get("content")).extracting(p -> p.get("id").asLong()).contains(onSale, fullPrice);

        // Ending the sale clears the compare-at price.
        Map<String, Object> update = productBody("Trail Jacket", "79.00");
        send(seller, put("/api/seller/products/" + onSale), update, 200);
        assertThat(send(null, get("/api/products/" + onSale), null, 200).path("compareAtPrice").isNumber()).isFalse();
    }

    @Test
    void compareAtPriceMustBeAboveThePriceAndContentIsValidated() throws Exception {
        Map<String, Object> notHigher = productBody("Lamp", "40.00");
        notHigher.put("compareAtPrice", "40.00");
        JsonNode error = send(seller, post("/api/seller/products"), notHigher, 422);
        assertThat(error.get("detail").asText()).contains("compare-at price");

        Map<String, Object> tooMany = productBody("Lamp", "40.00");
        tooMany.put("highlights", List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"));
        send(seller, post("/api/seller/products"), tooMany, 400);

        Map<String, Object> blankSpec = productBody("Lamp", "40.00");
        blankSpec.put("specs", List.of(Map.of("label", "", "value", "x")));
        send(seller, post("/api/seller/products"), blankSpec, 400);
    }

    @Test
    void boughtTogetherUsesOrderHistoryThenFallsBackToSimilarProducts() throws Exception {
        long camera = createProduct(seller, category, "Camera", "300.00", 50);
        long tripod = createProduct(seller, category, "Tripod", "40.00", 50);
        long bag = createProduct(seller, category, "Camera Bag", "30.00", 50);
        long strap = createProduct(seller, category, "Strap", "10.00", 50);

        for (int i = 0; i < 2; i++) {
            String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);
            addToCart(customer, camera, 1);
            addToCart(customer, tripod, 1);
            checkout(customer, "COD");
        }
        String other = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(other, camera, 1);
        addToCart(other, bag, 1);
        checkout(other, "COD");

        JsonNode together = send(null, get("/api/products/" + camera + "/bought-together").param("limit", "3"), null, 200);
        assertThat(together).extracting(p -> p.get("id").asLong()).containsExactly(tripod, bag, strap);

        send(null, get("/api/products/999999/bought-together"), null, 404);
    }

    private Map<String, Object> productBody(String name, String price) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("price", price);
        body.put("stock", 10);
        body.put("categoryId", category);
        return body;
    }
}
