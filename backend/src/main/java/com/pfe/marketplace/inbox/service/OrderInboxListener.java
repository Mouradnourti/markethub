package com.pfe.marketplace.inbox.service;

import com.pfe.marketplace.common.util.MoneyFormat;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.inbox.entity.NotificationType;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderItem;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Turns order events into bell notifications: new orders for sellers, parcel updates for customers. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInboxListener {

    private final OrderRepository orderRepository;
    private final InboxService inbox;
    private final AppProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderEvent(OrderEvent event) {
        try {
            Order order = orderRepository.findWithCustomerById(event.orderId()).orElse(null);
            if (order == null) {
                return;
            }
            if (event.type() == OrderEvent.Type.ORDER_PLACED) {
                order.getShopOrders().forEach(parcel -> notifySeller(order, parcel));
            } else {
                order.getShopOrders().stream()
                        .filter(parcel -> parcel.getId().equals(event.shopOrderId()))
                        .findFirst()
                        .ifPresent(parcel -> notifyCustomer(order, parcel, event.type()));
            }
        } catch (RuntimeException e) {
            log.warn("Could not create the notification for {} on order {}: {}", event.type(), event.orderId(),
                    e.getMessage());
        }
    }

    private void notifySeller(Order order, ShopOrder parcel) {
        int units = parcel.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        String body = "%d item%s · %s · %s".formatted(units, units > 1 ? "s" : "",
                MoneyFormat.format(properties.currency(), parcel.getSubtotal()),
                order.getPaymentMethod().name().equals("COD") ? "cash on delivery" : "paid by card");
        inbox.notify(parcel.getShop().getOwner().getId(), NotificationType.NEW_ORDER,
                "New order " + order.getReference(), body, "/seller/orders");
    }

    private void notifyCustomer(Order order, ShopOrder parcel, OrderEvent.Type type) {
        String shop = parcel.getShop().getName();
        String title = switch (type) {
            case ORDER_SHIPPED -> "Your parcel from " + shop + " is on its way";
            case ORDER_DELIVERED -> "Your parcel from " + shop + " was delivered";
            case ORDER_CANCELLED -> "Your parcel from " + shop + " was cancelled";
            case ORDER_RETURNED -> "Your return to " + shop + " was processed";
            case ORDER_PLACED -> "Order " + order.getReference() + " placed";
        };
        String body = type == OrderEvent.Type.ORDER_SHIPPED && parcel.getTrackingNumber() != null
                ? "Order " + order.getReference() + " · tracking " + parcel.getTrackingNumber()
                : "Order " + order.getReference();
        inbox.notify(order.getCustomer().getId(), NotificationType.ORDER_UPDATE, title, body,
                "/orders/" + order.getId());
    }
}
