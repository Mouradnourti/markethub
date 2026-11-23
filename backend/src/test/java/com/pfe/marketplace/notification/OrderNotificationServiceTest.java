package com.pfe.marketplace.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.support.Fixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderNotificationServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock NotificationClient client;
    Order order;

    @BeforeEach
    void setUp() {
        ShopOrder first = Fixtures.shopOrder(10, OrderStatus.SHIPPED, PaymentMethod.COD,
                Fixtures.product(1, Fixtures.shop(1, ShopStatus.APPROVED), "20.00", 5));
        order = first.getOrder();
        ShopOrder second = Fixtures.shopOrder(11, OrderStatus.PENDING, PaymentMethod.COD,
                Fixtures.product(2, Fixtures.shop(2, ShopStatus.APPROVED), "5.00", 5));
        order.addShopOrder(second);
        order.setCreatedAt(Instant.parse("2026-11-01T10:00:00Z"));
        order.setSubtotal(new BigDecimal("50.00"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("50.00"));
        order.setShippingFullName("Sara");
        when(orderRepository.findWithCustomerById(order.getId())).thenReturn(Optional.of(order));
    }

    private OrderNotificationService service(boolean enabled) {
        AppProperties properties = new AppProperties("eur", null, "key", null, null, null, null, null,
                new AppProperties.Notification("http://localhost", Duration.ofSeconds(1), enabled), null);
        return new OrderNotificationService(orderRepository, client, properties);
    }

    @Test
    void shippingEventTargetsTheRightParcel() {
        service(true).onOrderEvent(new OrderEvent(OrderEvent.Type.ORDER_SHIPPED, order.getId(), 11L));

        ArgumentCaptor<OrderDocument> document = ArgumentCaptor.forClass(OrderDocument.class);
        verify(client).sendOrderEvent(eq("ORDER_SHIPPED"), any(), document.capture(), eq(1));
        assertThat(document.getValue().shopOrders()).hasSize(2);
        assertThat(document.getValue().shopOrders().getFirst().items().getFirst().quantity()).isEqualTo(2);
        assertThat(document.getValue().currency()).isEqualTo("eur");
    }

    @Test
    void orderLevelEventHasNoParcelIndex() {
        service(true).onOrderEvent(new OrderEvent(OrderEvent.Type.ORDER_PLACED, order.getId(), null));
        verify(client).sendOrderEvent(eq("ORDER_PLACED"), any(), any(), eq(null));
    }

    @Test
    void notificationFailureIsSwallowed() {
        doThrow(new IllegalStateException("smtp down")).when(client).sendOrderEvent(any(), any(), any(), any());
        service(true).onOrderEvent(new OrderEvent(OrderEvent.Type.ORDER_PLACED, order.getId(), null));
    }

    @Test
    void disabledNotificationsSendNothing() {
        service(false).onOrderEvent(new OrderEvent(OrderEvent.Type.ORDER_PLACED, order.getId(), null));
        verify(client, never()).sendOrderEvent(any(), any(), any(), any());
    }

    @Test
    void invoiceIsOnlyAvailableToTheOwner() {
        when(orderRepository.findByIdAndCustomerId(order.getId(), 1L)).thenReturn(Optional.of(order));
        when(client.renderInvoice(any(), any())).thenReturn(new byte[]{37, 80, 68, 70});

        assertThat(service(true).invoice(1L, order.getId())).startsWith(37, 80);
        assertThatThrownBy(() -> service(true).invoice(2L, order.getId())).isInstanceOf(NotFoundException.class);
    }
}
