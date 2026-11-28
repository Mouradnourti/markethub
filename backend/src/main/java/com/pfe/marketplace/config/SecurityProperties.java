package com.pfe.marketplace.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Account protection settings ({@code app.security.*}).
 *
 * @param authRequestsPerMinute sign-in, sign-up and refresh calls allowed per client IP and minute
 * @param maxFailedLogins       consecutive wrong passwords (or codes) before the account is locked
 * @param lockoutDuration       how long a locked account stays locked
 * @param dataKey               key that encrypts two-factor secrets at rest (env {@code DATA_ENCRYPTION_KEY})
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("30") int authRequestsPerMinute,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("PT15M") Duration lockoutDuration,
        @DefaultValue("dev-only-data-key-change-me") String dataKey) {
}
