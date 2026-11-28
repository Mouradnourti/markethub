package com.pfe.marketplace.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfe.marketplace.support.ApiFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.test.context.TestPropertySource;

/** The Prometheus endpoint serves technical and marketplace metrics (enabled in Docker on the internal port). */
/* Spring Boot tests turn metric exporters off unless asked. */
@AutoConfigureObservability
@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,info,prometheus")
class MetricsIT extends ApiFixtures {

    @Test
    void prometheusExposesHttpJvmAndMarketplaceMetrics() throws Exception {
        String email = uniqueEmail("metrics");
        register(email, "CUSTOMER", null);
        send(null, post("/api/auth/login"), Map.of("email", email, "password", "wrong-password"), 401);

        String body = mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body)
                .contains("markethub_auth_failures_total")
                .contains("markethub_live_connections")
                .contains("http_server_requests_seconds_bucket")
                .contains("jvm_memory_used_bytes")
                .contains("application=\"markethub-backend\"");
    }
}
