package com.pfe.marketplace.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Builds marketplace state through the public API, the way real clients do. */
public abstract class ApiFixtures extends IntegrationTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected String adminToken() throws Exception {
        String email = uniqueEmail("admin");
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode("Admin12345"));
        admin.setFirstName("Ada");
        admin.setLastName("Admin");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        return login(email, "Admin12345");
    }

    protected long createCategory(String adminToken, String name) throws Exception {
        return send(adminToken, post("/api/admin/categories"), Map.of("name", name), 201).get("id").asLong();
    }

    /** Registers a seller and has the admin approve the shop. Returns the seller token. */
    protected String approvedSeller(String adminToken, String shopName) throws Exception {
        String email = uniqueEmail("seller");
        String token = register(email, "SELLER", shopName);
        Long shopId = jdbc.queryForObject(
                "select s.id from shops s join users u on u.id = s.owner_id where u.email = ?", Long.class, email);
        send(adminToken, patch("/api/admin/shops/" + shopId + "/status"), Map.of("status", "APPROVED"), 200);
        return token;
    }

    protected long createProduct(String sellerToken, long categoryId, String name, String price, int stock)
            throws Exception {
        Map<String, Object> body = Map.of("name", name, "description", name + " description", "price", price,
                "stock", stock, "categoryId", categoryId, "imageUrls", List.of("/uploads/demo/" + name + ".svg"));
        return send(sellerToken, post("/api/seller/products"), body, 201).get("id").asLong();
    }

    protected void addToCart(String customerToken, long productId, int quantity) throws Exception {
        send(customerToken, post("/api/cart/items"), Map.of("productId", productId, "quantity", quantity), 200);
    }

    protected JsonNode checkout(String customerToken, String paymentMethod) throws Exception {
        Map<String, Object> address = Map.of("fullName", "Sara Customer", "phone", "+33 6 12 34 56 78",
                "addressLine", "12 rue des Lilas", "city", "Lyon", "postalCode", "69001", "country", "France");
        return send(customerToken, post("/api/orders/checkout"),
                Map.of("paymentMethod", paymentMethod, "shippingAddress", address), 201);
    }

    protected JsonNode send(String token, MockHttpServletRequestBuilder request, Object body, int expectedStatus)
            throws Exception {
        ResultActions result = perform(token, request, body).andExpect(status().is(expectedStatus));
        String content = result.andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? objectMapper.createObjectNode() : readJson(content);
    }

    protected ResultActions perform(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    protected int stockOf(long productId) {
        return jdbc.queryForObject("select stock from products where id = ?", Integer.class, productId);
    }
}
