package com.pfe.marketplace.order.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Lifecycle of a shop order. See docs/architecture.md §4. */
public enum OrderStatus {
    AWAITING_PAYMENT, PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, RETURNED;

    private static final Map<OrderStatus, Set<OrderStatus>> SELLER_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(CONFIRMED, CANCELLED),
            CONFIRMED, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(DELIVERED, RETURNED));

    public boolean sellerCanMoveTo(OrderStatus next) {
        return SELLER_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean customerCanCancel() {
        return this == AWAITING_PAYMENT || this == PENDING;
    }

    /** Stock leaves the warehouse back to the shelf. */
    public boolean releasesStock() {
        return this == CANCELLED || this == RETURNED;
    }

    public boolean isFinal() {
        return this == DELIVERED || this == CANCELLED || this == RETURNED;
    }
}
