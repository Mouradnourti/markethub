package com.pfe.marketplace.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.marketplace.config.SecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-15T10:00:10Z"), ZoneOffset.UTC);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            new SecurityProperties(3, 5, Duration.ofMinutes(15), "key"), new ObjectMapper(), clock);

    @Test
    void blocksAnIpAfterTheLimitWithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("POST", "/api/auth/login", "10.0.0.1");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("50");
        assertThat(blocked.getContentAsString()).contains("Too many attempts");

        assertThat(call("POST", "/api/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
        assertThat(call("GET", "/api/products", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/cart/items", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse call(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
