package com.pfe.marketplace.order.event;

/**
 * Domain event published inside the order transaction. Listeners run after commit, so a notification
 * failure can never roll back an order. {@code shopOrderId} is null for order-level events.
 */
public record OrderEvent(Type type, Long orderId, Long shopOrderId) {

    public enum Type {
        ORDER_PLACED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED, ORDER_RETURNED
    }
}
