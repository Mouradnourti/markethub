package com.pfe.marketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthControllerIT extends IntegrationTest {

    @Test
    void registerLoginAndReadProfile() throws Exception {
        String email = uniqueEmail("customer");
        register(email, "CUSTOMER", null);

        String token = login(email.toUpperCase(), "Password123");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void sellerRegistrationCreatesPendingShop() throws Exception {
        String email = uniqueEmail("seller");
        register(email, "SELLER", "Atlas Crafts");

        String status = jdbc.queryForObject(
                "select s.status from shops s join users u on u.id = s.owner_id where u.email = ?", String.class, email);
        assertThat(status).isEqualTo("PENDING");
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail("dup");
        register(email, "CUSTOMER", null);

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Password123",
                                "firstName", "A", "lastName", "B"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPayloadReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email", "password", "short",
                                "firstName", "", "lastName", "B"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String email = uniqueEmail("wrongpw");
        register(email, "CUSTOMER", null);

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Nope12345"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void refreshTokenRotatesAndDetectsReuse() throws Exception {
        String email = uniqueEmail("refresh");
        register(email, "CUSTOMER", null);
        JsonNode loginBody = readJson(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Password123"))))
                .andReturn().getResponse().getContentAsString());
        String firstRefresh = loginBody.get("refreshToken").asText();

        JsonNode refreshed = readJson(mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String secondRefresh = refreshed.get("refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Re-using the first token revokes every session, including the second token.
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAuthenticationAndRole() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        String customerToken = register(uniqueEmail("role"), "CUSTOMER", null);
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = register(uniqueEmail("tamper"), "CUSTOMER", null);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }
}
