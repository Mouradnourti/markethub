package com.pfe.marketplace.shop.dto;

import com.pfe.marketplace.shop.entity.ShopStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public final class ShopDtos {

    private ShopDtos() {
    }

    public record ShopDto(
            Long id,
            String name,
            String slug,
            String description,
            String logoUrl,
            ShopStatus status,
            BigDecimal commissionRate,
            Instant createdAt) implements Serializable {
    }

    /** Admin view: includes the owner. */
    public record ShopAdminDto(
            Long id,
            String name,
            String slug,
            ShopStatus status,
            BigDecimal commissionRate,
            Long ownerId,
            String ownerEmail,
            String ownerName,
            long productCount,
            Instant createdAt) {
    }

    public record UpdateShopRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 2000) String description,
            @Size(max = 500) String logoUrl) {
    }

    public record ShopStatusRequest(@NotNull ShopStatus status) {
    }

    /** {@code null} rate removes the override and falls back to the platform default. */
    public record CommissionRequest(@DecimalMin("0") @DecimalMax("100") BigDecimal commissionRate) {
    }
}
