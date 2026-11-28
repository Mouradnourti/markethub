package com.pfe.marketplace.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Customer engagement settings ({@code app.engagement.*}): links in e-mails and the abandoned-cart reminder. */
@ConfigurationProperties(prefix = "app.engagement")
public record EngagementProperties(
        @DefaultValue("http://localhost:4200") String storefrontUrl,
        @DefaultValue("true") boolean cartReminderEnabled,
        @DefaultValue("PT24H") Duration cartReminderDelay) {

    public String link(String path) {
        String base = storefrontUrl.endsWith("/") ? storefrontUrl.substring(0, storefrontUrl.length() - 1) : storefrontUrl;
        return base + path;
    }
}
