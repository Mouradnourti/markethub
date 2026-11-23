package com.pfe.marketplace.order.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderRulesTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,CONFIRMED,true", "PENDING,CANCELLED,true", "PENDING,SHIPPED,false",
            "CONFIRMED,SHIPPED,true", "CONFIRMED,CANCELLED,true", "SHIPPED,DELIVERED,true",
            "SHIPPED,RETURNED,true", "SHIPPED,CANCELLED,false", "DELIVERED,RETURNED,false",
            "AWAITING_PAYMENT,PENDING,false", "CANCELLED,PENDING,false"})
    void sellerTransitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.sellerCanMoveTo(to)).isEqualTo(allowed);
    }

    @Test
    void customerCanOnlyCancelBeforeConfirmation() {
        assertThat(OrderStatus.PENDING.customerCanCancel()).isTrue();
        assertThat(OrderStatus.AWAITING_PAYMENT.customerCanCancel()).isTrue();
        assertThat(OrderStatus.CONFIRMED.customerCanCancel()).isFalse();
        assertThat(OrderStatus.DELIVERED.isFinal()).isTrue();
        assertThat(OrderStatus.SHIPPED.isFinal()).isFalse();
    }

    @Test
    void codIsPaidWhenAnyParcelIsDelivered() {
        Order order = order(PaymentMethod.COD, PaymentStatus.PENDING, OrderStatus.DELIVERED, OrderStatus.RETURNED);
        order.recomputePaymentStatus();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void codFailsWhenEveryParcelIsCancelledOrRefused() {
        Order order = order(PaymentMethod.COD, PaymentStatus.PENDING, OrderStatus.CANCELLED, OrderStatus.RETURNED);
        order.recomputePaymentStatus();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void paidCardOrderIsRefundedWhenEverythingIsReleased() {
        Order order = order(PaymentMethod.CARD, PaymentStatus.PAID, OrderStatus.RETURNED);
        order.recomputePaymentStatus();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void inProgressOrdersKeepTheirPaymentStatus() {
        Order order = order(PaymentMethod.CARD, PaymentStatus.PAID, OrderStatus.SHIPPED, OrderStatus.CANCELLED);
        order.recomputePaymentStatus();
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void moveToAppendsHistoryAndNetAmountSubtractsCommission() {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setSubtotal(new BigDecimal("100.00"));
        shopOrder.setCommissionAmount(new BigDecimal("12.50"));
        shopOrder.moveTo(OrderStatus.PENDING, "placed", null);
        shopOrder.moveTo(OrderStatus.CONFIRMED, null, null);

        assertThat(shopOrder.getHistory()).extracting(OrderStatusHistory::getStatus)
                .containsExactly(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        assertThat(shopOrder.getNetAmount()).isEqualByComparingTo("87.50");
    }

    private static Order order(PaymentMethod method, PaymentStatus paymentStatus, OrderStatus... statuses) {
        Order order = new Order();
        order.setPaymentMethod(method);
        order.setPaymentStatus(paymentStatus);
        for (OrderStatus status : statuses) {
            ShopOrder shopOrder = new ShopOrder();
            shopOrder.setStatus(status);
            order.addShopOrder(shopOrder);
        }
        return order;
    }
}
