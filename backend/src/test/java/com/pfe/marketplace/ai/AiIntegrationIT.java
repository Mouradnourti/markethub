package com.pfe.marketplace.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.ai.service.ReturnRiskBackfillJob;
import com.pfe.marketplace.security.InternalApiKeyFilter;
import com.pfe.marketplace.support.ApiFixtures;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Backend ↔ AI service contract, against a fake AI HTTP server. */
class AiIntegrationIT extends ApiFixtures {

    private static final List<Long> RECOMMENDED = new CopyOnWriteArrayList<>();
    private static final List<Long> SEARCH_HITS = new CopyOnWriteArrayList<>();
    private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer FAKE_AI = startFakeAi();

    @Autowired ReturnRiskBackfillJob backfillJob;

    @DynamicPropertySource
    static void aiUrl(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", () -> "http://localhost:" + FAKE_AI.getAddress().getPort());
        registry.add("app.ai.timeout", () -> "2s");
    }

    @AfterAll
    static void stop() {
        FAKE_AI.stop(0);
    }

    @Test
    void codCheckoutStoresTheAiRiskScoreAndSellersSeeIt() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "AI " + System.nanoTime());
        String seller = approvedSeller(admin, "AI Shop");
        long product = createProduct(seller, category, "Speaker", "80.00", 10);
        String customer = register(uniqueEmail("ai"), "CUSTOMER", null);
        addToCart(customer, product, 1);

        JsonNode order = checkout(customer, "COD").get("order");

        assertThat(order.get("returnRiskScore").decimalValue()).isEqualByComparingTo("0.4200");
        assertThat(REQUESTS).anyMatch(body -> body.contains("\"firstOrder\":1") && body.contains("\"city\":\"Lyon\""));
        JsonNode parcel = send(seller, get("/api/seller/orders"), null, 200).get("content").get(0);
        assertThat(parcel.get("returnRiskScore").decimalValue()).isEqualByComparingTo("0.4200");
    }

    @Test
    void backfillScoresOpenCodOrdersWithoutScore() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "Backfill " + System.nanoTime());
        String seller = approvedSeller(admin, "Backfill Shop");
        long product = createProduct(seller, category, "Lamp", "15.00", 10);
        String customer = register(uniqueEmail("backfill"), "CUSTOMER", null);
        addToCart(customer, product, 1);
        long orderId = checkout(customer, "COD").get("order").get("id").asLong();
        jdbc.update("update orders set return_risk_score = null where id = ?", orderId);

        assertThat(backfillJob.backfill()).isPositive();
        assertThat(jdbc.queryForObject("select return_risk_score from orders where id = ?", Double.class, orderId))
                .isEqualTo(0.42);
    }

    @Test
    void recommendationsUseAiPicksThenBestSellersAndSkipOwnedProducts() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "Reco " + System.nanoTime());
        String seller = approvedSeller(admin, "Reco Shop");
        long owned = createProduct(seller, category, "Owned", "10.00", 10);
        long first = createProduct(seller, category, "Pick one", "10.00", 10);
        long second = createProduct(seller, category, "Pick two", "10.00", 10);
        long bestSeller = createProduct(seller, category, "Best seller", "10.00", 10);
        jdbc.update("update products set sold_count = 1000000 where id = ?", bestSeller);
        String customer = register(uniqueEmail("reco"), "CUSTOMER", null);
        addToCart(customer, owned, 1);
        checkout(customer, "COD");
        RECOMMENDED.clear();
        RECOMMENDED.addAll(List.of(owned, second, first, 9_999_999L));

        JsonNode picks = send(customer, get("/api/recommendations?limit=4"), null, 200);

        assertThat(picks).hasSize(4);
        assertThat(picks.get(0).get("id").asLong()).isEqualTo(second);
        assertThat(picks.get(1).get("id").asLong()).isEqualTo(first);
        assertThat(picks.findValuesAsText("id")).doesNotContain(String.valueOf(owned))
                .contains(String.valueOf(bestSeller));
        jdbc.update("update products set sold_count = 0 where id = ?", bestSeller);
    }

    @Test
    void sellerForecastAndInternalDataset() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "Forecast " + System.nanoTime());
        String seller = approvedSeller(admin, "Forecast Shop");
        long product = createProduct(seller, category, "Bottle", "20.00", 500);
        String customer = register(uniqueEmail("forecast"), "CUSTOMER", null);
        addToCart(customer, product, 1);
        long orderId = checkout(customer, "COD").get("order").get("id").asLong();

        JsonNode tooEarly = send(seller, get("/api/seller/forecast"), null, 200);
        assertThat(tooEarly.get("available").asBoolean()).isFalse();

        // Spread the sale over 20 past days so the shop has enough history.
        for (int day = 1; day <= 20; day++) {
            addToCart(customer, product, 1);
            long id = checkout(customer, "COD").get("order").get("id").asLong();
            jdbc.update("update shop_orders set created_at = now() - make_interval(days => ?) where order_id = ?", day, id);
        }
        JsonNode forecast = send(seller, get("/api/seller/forecast?days=7"), null, 200);
        assertThat(forecast.get("available").asBoolean()).isTrue();
        assertThat(forecast.get("forecast")).hasSize(2);
        assertThat(forecast.get("model").asText()).contains("Ridge");
        assertThat(orderId).isPositive();

        mockMvc.perform(get("/api/internal/ml/dataset")).andExpect(status().isUnauthorized());
        JsonNode dataset = readJson(mockMvc.perform(get("/api/internal/ml/dataset")
                        .header(InternalApiKeyFilter.HEADER, "test-internal-key"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(dataset.get("products").size()).isPositive();
        assertThat(dataset.get("interactions").get(0).has("user_id")).isTrue();
        assertThat(dataset.has("codOrders")).isTrue();
    }

    @Test
    void sellersDraftProductCopyWithTheAiWriter() throws Exception {
        String admin = adminToken();
        long parent = createCategory(admin, "Fashion " + System.nanoTime());
        long child = send(admin, post("/api/admin/categories"),
                java.util.Map.of("name", "Jackets " + System.nanoTime(), "parentId", parent), 201).get("id").asLong();
        String seller = approvedSeller(admin, "Copy Shop");

        JsonNode draft = send(seller, post("/api/seller/products/describe"), java.util.Map.of(
                "name", "Rain Jacket", "categoryId", child, "keywords", java.util.List.of(" waterproof "),
                "tone", "premium"), 200);
        assertThat(draft.get("tagline").asText()).isEqualTo("Stay dry.");
        assertThat(draft.get("specs").get(0).get("value").asText()).isEqualTo("320 g");
        assertThat(REQUESTS.getLast()).contains("\"category\":\"Fashion").contains("/ Jackets")
                .contains("\"keywords\":[\"waterproof\"]").contains("\"tone\":\"premium\"");

        send(seller, post("/api/seller/products/describe"), java.util.Map.of("name", "Rain Jacket", "tone", "loud"), 400);
        String customer = register(uniqueEmail("copy"), "CUSTOMER", null);
        send(customer, post("/api/seller/products/describe"), java.util.Map.of("name", "Rain Jacket"), 403);
    }

    @Test
    void smartSearchUsesTheAiIndexAndSellersGetPriceHints() throws Exception {
        String admin = adminToken();
        long category = createCategory(admin, "Mugs " + System.nanoTime());
        String seller = approvedSeller(admin, "Mug Shop");
        long thermos = createProduct(seller, category, "Steel Thermos", "25.00", 10);
        SEARCH_HITS.clear();
        SEARCH_HITS.addAll(List.of(thermos, 999_999L));

        JsonNode result = send(null, get("/api/products/smart-search").param("q", "keep coffee hot"), null, 200);
        assertThat(result.get("strategy").asText()).isEqualTo("semantic");
        assertThat(result.get("products")).extracting(p -> p.get("id").asLong()).containsExactly(thermos);
        assertThat(REQUESTS.getLast()).contains("\"query\":\"keep coffee hot\"");

        JsonNode hint = send(seller, post("/api/seller/products/price-hint"),
                java.util.Map.of("name", "Travel Mug", "categoryId", category), 200);
        assertThat(hint.get("suggested").decimalValue()).isEqualByComparingTo("24.99");
        assertThat(hint.get("comparables")).hasSize(1);
        assertThat(REQUESTS.getLast()).contains("\"category\":\"Mugs");
    }

    private static HttpServer startFakeAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/return-risk", exchange -> respond(exchange,
                    "{\"score\":0.42,\"level\":\"high\",\"reasons\":[\"First order from this customer\"],\"model\":\"lr\"}"));
            server.createContext("/recommendations", exchange -> respond(exchange,
                    "{\"productIds\":" + RECOMMENDED + ",\"strategy\":\"personalized\"}"));
            server.createContext("/copywriter", exchange -> respond(exchange, """
                    {"tagline":"Stay dry.","description":"A great jacket.","highlights":["Waterproof"],
                     "specs":[{"label":"Weight","value":"320 g"}],"suggestedSpecLabels":["Fit"],
                     "categoryProfile":"fashion"}"""));
            server.createContext("/search", exchange -> respond(exchange, "{\"hits\":" + SEARCH_HITS.stream()
                    .map(id -> "{\"productId\":" + id + ",\"score\":0.5}").toList() + ",\"model\":\"tfidf+lsa\"}"));
            server.createContext("/price-hint", exchange -> respond(exchange, """
                    {"low":20,"typical":25,"high":30,"suggested":24.99,"confidence":"medium",
                     "comparables":[{"productId":1,"name":"Mug","price":25,"similarity":0.6}]}"""));
            server.createContext("/forecast", exchange -> respond(exchange, """
                    {"model":"Ridge regression","mae":3.1,"baselineMae":4.2,"forecast":[
                     {"date":"2030-01-01","revenue":10,"lower":5,"upper":15},
                     {"date":"2030-01-02","revenue":12,"lower":6,"upper":18}]}"""));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String json) throws IOException {
        REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
