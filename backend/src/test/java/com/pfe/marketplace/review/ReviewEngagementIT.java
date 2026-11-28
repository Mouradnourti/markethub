package com.pfe.marketplace.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.support.ApiFixtures;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** Helpful votes, star breakdown and sorting, review photos, and public seller replies. */
class ReviewEngagementIT extends ApiFixtures {

    private String seller;
    private long product;

    @BeforeEach
    void setUp() throws Exception {
        String admin = adminToken();
        seller = approvedSeller(admin, "Review Shop " + System.nanoTime());
        long category = createCategory(admin, "Reviews " + System.nanoTime());
        product = createProduct(seller, category, "Kettle", "30.00", 50);
    }

    @Test
    void reviewsCanBeVotedSortedFilteredAndAnswered() throws Exception {
        String alice = deliveredCustomer();
        String bob = deliveredCustomer();
        String reader = register(uniqueEmail("reader"), "CUSTOMER", null);

        long aliceReview = review(alice, 5, "Boils fast and looks great.");
        long bobReview = review(bob, 2, "The lid feels flimsy.");

        // Votes are idempotent and authors cannot vote for themselves.
        send(reader, post(reviewUrl(bobReview) + "/helpful"), null, 200);
        JsonNode voted = send(reader, post(reviewUrl(bobReview) + "/helpful"), null, 200);
        assertThat(voted.get("helpfulCount").asInt()).isEqualTo(1);
        assertThat(voted.get("votedHelpful").asBoolean()).isTrue();
        send(alice, post(reviewUrl(bobReview) + "/helpful"), null, 200);
        send(bob, post(reviewUrl(bobReview) + "/helpful"), null, 422);

        JsonNode helpfulFirst = send(reader, get(reviewsUrl()).param("sort", "HELPFUL"), null, 200).get("content");
        assertThat(helpfulFirst.get(0).get("id").asLong()).isEqualTo(bobReview);
        assertThat(helpfulFirst.get(0).get("helpfulCount").asInt()).isEqualTo(2);
        assertThat(helpfulFirst.get(0).get("votedHelpful").asBoolean()).isTrue();
        assertThat(helpfulFirst.get(1).get("votedHelpful").asBoolean()).isFalse();

        JsonNode fiveStars = send(null, get(reviewsUrl()).param("rating", "5"), null, 200).get("content");
        assertThat(fiveStars).extracting(r -> r.get("id").asLong()).containsExactly(aliceReview);
        JsonNode lowest = send(null, get(reviewsUrl()).param("sort", "LOWEST"), null, 200).get("content");
        assertThat(lowest.get(0).get("rating").asInt()).isEqualTo(2);

        send(reader, delete(reviewUrl(bobReview) + "/helpful"), null, 200);
        JsonNode summary = send(null, get(reviewsUrl() + "/summary"), null, 200);
        assertThat(summary.get("total").asLong()).isEqualTo(2);
        assertThat(summary.get("counts")).extracting(JsonNode::asLong).containsExactly(0L, 1L, 0L, 0L, 1L);
        assertThat(summary.get("average").decimalValue()).isEqualByComparingTo("3.50");

        // The seller answers the critical review; its author is notified once.
        JsonNode inbox = send(seller, get("/api/seller/reviews").param("unanswered", "true"), null, 200).get("content");
        assertThat(inbox).hasSize(2);
        JsonNode reply = send(seller, put("/api/seller/reviews/" + bobReview + "/reply"),
                Map.of("reply", "Sorry about that! We sent you a replacement lid."), 200);
        assertThat(reply.get("sellerReply").asText()).startsWith("Sorry");
        send(seller, put("/api/seller/reviews/" + bobReview + "/reply"), Map.of("reply", "Edited answer."), 200);
        assertThat(send(seller, get("/api/seller/reviews").param("unanswered", "true"), null, 200).get("content"))
                .hasSize(1);
        JsonNode bobNotifications = send(bob, get("/api/notifications"), null, 200).get("content");
        assertThat(bobNotifications).filteredOn(n -> n.get("type").asText().equals("REVIEW_REPLY")).hasSize(1);
        JsonNode publicReview = send(null, get(reviewsUrl()).param("sort", "LOWEST"), null, 200).get("content").get(0);
        assertThat(publicReview.get("sellerReply").asText()).isEqualTo("Edited answer.");

        String otherSeller = approvedSeller(adminToken(), "Other Review Shop " + System.nanoTime());
        send(otherSeller, put("/api/seller/reviews/" + bobReview + "/reply"), Map.of("reply", "Hijack"), 404);
        send(seller, delete("/api/seller/reviews/" + bobReview + "/reply"), null, 200);
    }

    @Test
    void onlyTheAuthorAddsPhotosUpToThree() throws Exception {
        String author = deliveredCustomer();
        String stranger = register(uniqueEmail("stranger"), "CUSTOMER", null);
        long reviewId = review(author, 4, "Nice!");

        for (int i = 0; i < 3; i++) {
            JsonNode updated = upload(author, reviewId, 200);
            assertThat(updated.get("photoUrls")).hasSize(i + 1);
            assertThat(updated.get("photoUrls").get(i).asText()).startsWith("/uploads/reviews/");
        }
        upload(author, reviewId, 422);
        upload(stranger, reviewId, 403);
        assertThat(send(null, get(reviewsUrl() + "/summary"), null, 200).get("withPhotos").asLong()).isEqualTo(1);
    }

    private JsonNode upload(String token, long reviewId, int expected) throws Exception {
        var file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2});
        String body = mockMvc.perform(multipart(reviewUrl(reviewId) + "/photos").file(file)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return readJson(body);
    }

    private String deliveredCustomer() throws Exception {
        String customer = register(uniqueEmail("buyer"), "CUSTOMER", null);
        addToCart(customer, product, 1);
        long parcel = checkout(customer, "COD").get("order").get("shopOrders").get(0).get("id").asLong();
        for (String next : new String[]{"CONFIRMED", "SHIPPED", "DELIVERED"}) {
            send(seller, patch("/api/seller/orders/" + parcel + "/status"), Map.of("status", next), 200);
        }
        return customer;
    }

    private long review(String customer, int rating, String comment) throws Exception {
        return send(customer, post(reviewsUrl()), Map.of("rating", rating, "comment", comment), 201).get("id").asLong();
    }

    private String reviewsUrl() {
        return "/api/products/" + product + "/reviews";
    }

    private String reviewUrl(long reviewId) {
        return reviewsUrl() + "/" + reviewId;
    }
}
