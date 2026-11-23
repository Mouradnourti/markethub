package com.pfe.marketplace.order.dto;

import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ShippingAddress(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Pattern(regexp = "^[+0-9 ()-]{6,30}$", message = "must be a valid phone number") String phone,
            @NotBlank @Size(max = 255) String addressLine,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 20) String postalCode,
            @NotBlank @Size(max = 80) String country) {
    }

    public record CheckoutRequest(@NotNull PaymentMethod paymentMethod, @NotNull @Valid ShippingAddress shippingAddress) {
    }

    /** Client-side payment data for CARD orders (null for COD). */
    public record PaymentInitDto(String provider, String clientSecret, String publishableKey, BigDecimal amount,
                                 String currency) {
    }

    public record CheckoutResponse(OrderDto order, PaymentInitDto payment) {
    }

    public record OrderItemDto(Long productId, String productName, BigDecimal unitPrice, int quantity,
                               BigDecimal lineTotal) {
    }

    public record StatusChangeDto(OrderStatus status, String note, Instant changedAt) {
    }

    public record ShopOrderDto(
            Long id,
            Long shopId,
            String shopName,
            OrderStatus status,
            BigDecimal subtotal,
            String trackingNumber,
            List<OrderItemDto> items,
            List<StatusChangeDto> history,
            boolean returnRequested,
            Instant updatedAt) {
    }

    public record OrderDto(
            Long id,
            String reference,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal total,
            ShippingAddress shippingAddress,
            BigDecimal returnRiskScore,
            List<ShopOrderDto> shopOrders,
            Instant createdAt) {
    }

    /** What a seller sees: their own parcel plus the delivery details. */
    public record SellerOrderDto(
            Long id,
            String orderReference,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            BigDecimal subtotal,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            String trackingNumber,
            String customerName,
            ShippingAddress shippingAddress,
            BigDecimal returnRiskScore,
            List<OrderItemDto> items,
            List<StatusChangeDto> history,
            Instant createdAt) {
    }

    public record UpdateShopOrderStatusRequest(
            @NotNull OrderStatus status,
            @Size(max = 60) String trackingNumber,
            @Size(max = 255) String note) {
    }

    public record CancelOrderRequest(@Size(max = 255) String reason) {
    }
}
