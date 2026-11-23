package com.pfe.marketplace.notification;

import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends order e-mails through the notification service once the order transaction has committed, on a background
 * thread: an e-mail outage is logged and never affects the order itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNotificationService {

    private final OrderRepository orderRepository;
    private final NotificationClient client;
    private final AppProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onOrderEvent(OrderEvent event) {
        if (!properties.notification().enabled()) {
            return;
        }
        try {
            Order order = load(event.orderId());
            User customer = order.getCustomer();
            client.sendOrderEvent(event.type().name(), new OrderDocument.Person(customer.getFullName(), customer.getEmail()),
                    OrderDocument.of(order, properties.currency()), parcelIndex(order, event.shopOrderId()));
        } catch (RuntimeException e) {
            log.warn("Could not send {} e-mail for order {}: {}", event.type(), event.orderId(), e.getMessage());
        }
    }

    /** PDF invoice of a customer's order, rendered by the notification service. */
    @Transactional(readOnly = true)
    public byte[] invoice(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        User customer = order.getCustomer();
        return client.renderInvoice(new OrderDocument.Person(customer.getFullName(), customer.getEmail()),
                OrderDocument.of(order, properties.currency()));
    }

    private Order load(Long orderId) {
        return orderRepository.findWithCustomerById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private static Integer parcelIndex(Order order, Long shopOrderId) {
        if (shopOrderId == null) {
            return null;
        }
        List<ShopOrder> parcels = order.getShopOrders();
        for (int i = 0; i < parcels.size(); i++) {
            if (parcels.get(i).getId().equals(shopOrderId)) {
                return i;
            }
        }
        return null;
    }
}
