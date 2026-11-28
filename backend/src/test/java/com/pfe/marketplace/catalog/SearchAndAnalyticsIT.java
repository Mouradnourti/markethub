package com.pfe.marketplace.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.support.ApiFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Typo-tolerant search, relevance sort, instant suggestions, filter counts and seller conversion analytics. */
class SearchAndAnalyticsIT extends ApiFixtures {

    private String admin;
    private String seller;
    private long shopId;
    private long audio;
    private long lighting;
    private long headphones;
    private long stand;
    private long lamp;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        String shopName = "Search Shop " + System.nanoTime();
        seller = approvedSeller(admin, shopName);
        shopId = jdbc.queryForObject("select id from shops where name = ?", Long.class, shopName);
        audio = createCategory(admin, "Audio " + System.nanoTime());
        lighting = createCategory(admin, "Lighting " + System.nanoTime());
        headphones = createProduct(seller, audio, "Quixotic Wireless Headphones", "99.00", 10);
        stand = createProduct(seller, audio, "Quixotic Headphone Stand", "25.00", 0);
        lamp = createProduct(seller, lighting, "Quixotic Desk Lamp", "45.00", 4);
    }

    @Test
    void searchToleratesTyposAndRanksTheClosestNameFirst() throws Exception {
        JsonNode typo = search("hedphones", "NEWEST");
        assertThat(ids(typo)).contains(headphones).doesNotContain(lamp);

        assertThat(ids(search("desk lamp", "RELEVANCE")).getFirst()).isEqualTo(lamp);
        assertThat(ids(search("quixotic wireless", "RELEVANCE")).getFirst()).isEqualTo(headphones);
        assertThat(ids(search("xyz", "NEWEST"))).isEmpty();
    }

    @Test
    void suggestionsAndFacetsDescribeTheResults() throws Exception {
        JsonNode suggestions = send(null, get("/api/products/suggest").param("q", "quixotc"), null, 200);
        assertThat(suggestions.get("products")).extracting(p -> p.get("id").asLong()).contains(headphones, lamp);
        assertThat(send(null, get("/api/products/suggest").param("q", "q"), null, 200).get("products")).isEmpty();

        JsonNode facets = send(null, get("/api/products/facets").param("shopId", String.valueOf(shopId))
                .param("categoryId", String.valueOf(audio)), null, 200);
        assertThat(facets.get("total").asLong()).isEqualTo(2);
        assertThat(facets.get("inStock").asLong()).isEqualTo(1);
        assertThat(facets.get("minPrice").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(facets.get("maxPrice").decimalValue()).isEqualByComparingTo("99.00");
        // Category counts ignore the selected category so the shopper can switch.
        assertThat(facets.get("categories")).extracting(c -> c.get("id").asLong() + ":" + c.get("count").asLong())
                .containsExactlyInAnyOrder(audio + ":2", lighting + ":1");
    }

    @Test
    void sellerAnalyticsJoinPageViewsWithOrders() throws Exception {
        for (int i = 0; i < 4; i++) {
            send(null, get("/api/products/" + headphones), null, 200);
        }
        String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);
        send(customer, put("/api/wishlist/" + lamp), null, 204);
        addToCart(customer, headphones, 2);
        checkout(customer, "COD");

        JsonNode analytics = null;
        for (int i = 0; i < 50; i++) {
            analytics = send(seller, get("/api/seller/analytics").param("days", "30"), null, 200);
            if (analytics.get("views").asLong() >= 4) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(analytics.get("days").asInt()).isEqualTo(30);
        assertThat(analytics.get("views").asLong()).isEqualTo(4);
        assertThat(analytics.get("orders").asLong()).isEqualTo(1);
        assertThat(analytics.get("units").asLong()).isEqualTo(2);
        assertThat(analytics.get("conversionRate").decimalValue()).isEqualByComparingTo("25.0");
        assertThat(analytics.get("wishlistSaves").asLong()).isEqualTo(1);
        assertThat(analytics.get("daily")).hasSize(30);

        JsonNode best = analytics.get("products").get(0);
        assertThat(best.get("productId").asLong()).isEqualTo(headphones);
        assertThat(best.get("views").asLong()).isEqualTo(4);
        assertThat(best.get("revenue").decimalValue()).isEqualByComparingTo("198.00");
        assertThat(best.get("conversionRate").decimalValue()).isEqualByComparingTo("25.0");
    }

    @Test
    void smartSearchFallsBackToKeywordSearchWhenTheAiIsDown() throws Exception {
        JsonNode result = send(null, get("/api/products/smart-search").param("q", "quixotic desk lamp"), null, 200);
        assertThat(result.get("strategy").asText()).isEqualTo("keyword");
        // Every test method creates its own "Quixotic Desk Lamp", so only check this one is found.
        assertThat(result.get("products")).extracting(p -> p.get("id").asLong()).contains(lamp);
        assertThat(result.get("products").get(0).get("name").asText()).isEqualTo("Quixotic Desk Lamp");
    }

    private JsonNode search(String q, String sort) throws Exception {
        return send(null, get("/api/products").param("q", q).param("sort", sort)
                .param("shopId", String.valueOf(shopId)), null, 200);
    }

    private static List<Long> ids(JsonNode page) {
        List<Long> ids = new java.util.ArrayList<>();
        page.get("content").forEach(p -> ids.add(p.get("id").asLong()));
        return ids;
    }
}
