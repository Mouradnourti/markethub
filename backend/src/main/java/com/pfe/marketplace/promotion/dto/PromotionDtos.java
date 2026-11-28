package com.pfe.marketplace.promotion.dto;

import com.pfe.marketplace.promotion.entity.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;

public final class PromotionDtos {

    private PromotionDtos() {
    }

    public enum CouponStatus {
        ACTIVE, SCHEDULED, EXPIRED, USED_UP, PAUSED
    }

    public record CouponRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$",
                    message = "must be 3 to 30 letters, digits, dashes or underscores") String code,
            @NotNull CouponType type,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal value,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal minSubtotal,
            Instant startsAt,
            Instant endsAt,
            @Min(1) Integer maxUses,
            Boolean oncePerCustomer,
            Boolean featured,
            Boolean active) {
    }

    public record CouponDto(
            Long id,
            String code,
            CouponType type,
            BigDecimal value,
            BigDecimal minSubtotal,
            Instant startsAt,
            Instant endsAt,
            Integer maxUses,
            int usedCount,
            boolean oncePerCustomer,
            boolean featured,
            boolean active,
            CouponStatus status,
            String label,
            Instant createdAt) {
    }

    /** A code applied to the cart, with the amount it takes off. */
    public record AppliedCouponDto(String code, Long shopId, String shopName, String label, BigDecimal discount) {
    }

    /** A featured code advertised on a shop's product pages. */
    public record OfferDto(String code, String label, String shopName, BigDecimal minSubtotal, Instant endsAt) {
    }
}
