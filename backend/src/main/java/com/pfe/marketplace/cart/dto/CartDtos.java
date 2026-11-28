package com.pfe.marketplace.cart.dto;

import com.pfe.marketplace.promotion.dto.PromotionDtos.AppliedCouponDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record CartItemDto(
            Long productId,
            String productName,
            String imageUrl,
            Long shopId,
            String shopName,
            BigDecimal unitPrice,
            int quantity,
            int availableStock,
            boolean available,
            BigDecimal lineTotal) {
    }

    public record CartDto(
            List<CartItemDto> items,
            int itemCount,
            BigDecimal subtotal,
            BigDecimal discount,
            AppliedCouponDto coupon,
            BigDecimal shippingFee,
            BigDecimal total) {
    }

    public record AddCartItemRequest(@NotNull Long productId, @Min(1) @Max(99) int quantity) {
    }

    public record UpdateCartItemRequest(@Min(1) @Max(99) int quantity) {
    }
}
