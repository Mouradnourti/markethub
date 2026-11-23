package com.pfe.marketplace.settings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record PlatformSettingsDto(
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal defaultCommissionRate,
            @NotNull @DecimalMin("0") @DecimalMax("1000") BigDecimal shippingFlatFee,
            @NotNull @DecimalMin("0") @DecimalMax("100000") BigDecimal freeShippingThreshold) {
    }

    /** Public, non-secret configuration the SPA needs at startup. */
    public record PublicConfigDto(
            String currency,
            boolean stripeEnabled,
            String stripePublishableKey,
            BigDecimal shippingFlatFee,
            BigDecimal freeShippingThreshold) {
    }
}
