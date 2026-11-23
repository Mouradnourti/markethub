package com.pfe.marketplace.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full Spring context on a real PostgreSQL, with helpers to call the API as a given user. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase db = TestDatabase.get();
        registry.add("spring.datasource.url", db::jdbcUrl);
        registry.add("spring.datasource.username", db::username);
        registry.add("spring.datasource.password", db::password);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected JsonNode readJson(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    /** Registers a user and returns its access token. */
    protected String register(String email, String accountType, String shopName) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of(
                "email", email, "password", "Password123", "firstName", "Test", "lastName", "User",
                "accountType", accountType));
        if (shopName != null) {
            body.put("shopName", shopName);
        }
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return readJson(response).get("accessToken").asText();
    }

    protected String login(String email, String password) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return readJson(response).get("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }
}
