package com.pfe.marketplace.notification;

import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.ShopOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The order as sent to the notification service (contract: notification-service OrderPayloadRules). */
public record OrderDocument(
        String reference,
        Instant createdAt,
        String paymentMethod,
        String paymentStatus,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal shippingFee,
        BigDecimal total,
        Address shippingAddress,
        List<Parcel> shopOrders) {

    public record Address(String fullName, String phone, String addressLine, String city, String postalCode,
                          String country) {
    }

    /** {@code subtotal} is what the customer pays for the parcel's items, after {@code discountAmount}. */
    public record Parcel(String shopName, String status, BigDecimal subtotal, BigDecimal discountAmount,
                         String couponCode, String trackingNumber, List<Line> items) {
    }

    public record Line(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record Person(String name, String email) {
    }

    public static OrderDocument of(Order order, String currency) {
        return new OrderDocument(order.getReference(), order.getCreatedAt(), order.getPaymentMethod().name(),
                order.getPaymentStatus().name(), currency, order.getSubtotal(), order.getDiscountTotal(), order.getShippingFee(), order.getTotal(),
                new Address(order.getShippingFullName(), order.getShippingPhone(), order.getShippingAddressLine(),
                        order.getShippingCity(), order.getShippingPostalCode(), order.getShippingCountry()),
                order.getShopOrders().stream().map(OrderDocument::parcel).toList());
    }

    private static Parcel parcel(ShopOrder shopOrder) {
        return new Parcel(shopOrder.getShop().getName(), shopOrder.getStatus().name(), shopOrder.getSubtotal(),
                shopOrder.getDiscountAmount(), shopOrder.getCouponCode(), shopOrder.getTrackingNumber(), shopOrder.getItems().stream()
                .map(i -> new Line(i.getProductName(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal()))
                .toList());
    }
}
