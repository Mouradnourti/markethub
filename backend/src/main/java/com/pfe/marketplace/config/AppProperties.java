package com.pfe.marketplace.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed view of the {@code app.*} configuration. All secrets come from environment variables. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String currency,
        String publicBaseUrl,
        @NotBlank String internalApiKey,
        Jwt jwt,
        Cors cors,
        Uploads uploads,
        Stripe stripe,
        Ai ai,
        Notification notification,
        BootstrapAdmin bootstrapAdmin) {

    public record Jwt(@NotBlank @Size(min = 32) String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Uploads(String dir) {
    }

    public record Stripe(String secretKey, String publishableKey, String webhookSecret) {
        public boolean enabled() {
            return secretKey != null && !secretKey.isBlank();
        }
    }

    /** {@code writerTimeout} is longer: with an API key, the description writer calls a language model. */
    public record Ai(String baseUrl, Duration timeout, Duration writerTimeout) {
        public Duration writerTimeoutOrDefault() {
            return writerTimeout == null ? Duration.ofSeconds(30) : writerTimeout;
        }
    }

    public record Notification(String baseUrl, Duration timeout, boolean enabled) {
    }

    public record BootstrapAdmin(String email, String password) {
    }
}
