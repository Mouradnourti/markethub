package com.pfe.marketplace.payment.service;

import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.order.dto.OrderDtos.PaymentInitDto;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.order.service.OrderLifecycleService;
import com.pfe.marketplace.payment.entity.Payment;
import com.pfe.marketplace.payment.gateway.PaymentGateway;
import com.pfe.marketplace.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Card payment flow: create an intent at checkout, then confirm it (client callback or Stripe webhook). */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway gateway;
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderLifecycleService lifecycle;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;

    /** Returns the data the browser needs to collect the card, or null for cash on delivery. */
    @Transactional
    public PaymentInitDto initiate(Order order) {
        if (order.getPaymentMethod() != PaymentMethod.CARD) {
            return null;
        }
        PaymentGateway.Intent intent = gateway.createIntent(order.getReference(), order.getTotal(), properties.currency());
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(Payment.Provider.valueOf(gateway.providerName()));
        payment.setProviderReference(intent.reference());
        payment.setAmount(order.getTotal());
        payment.setCurrency(properties.currency());
        paymentRepository.save(payment);
        String publishableKey = properties.stripe() == null ? null : properties.stripe().publishableKey();
        return new PaymentInitDto(gateway.providerName(), intent.clientSecret(),
                gateway.providerName().equals("STRIPE") ? publishableKey : null, order.getTotal(), properties.currency());
    }

    /** Called by the SPA once the card form completes. Idempotent. */
    @Transactional
    public PaymentStatus confirm(Long customerId, Long orderId, boolean simulateDecline) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (order.getPaymentMethod() != PaymentMethod.CARD) {
            throw new BusinessException("This order is paid cash on delivery");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING) {
            return order.getPaymentStatus();
        }
        Payment payment = paymentRepository.findFirstByOrderIdOrderByIdDesc(orderId)
                .orElseThrow(() -> new NotFoundException("No payment was started for this order"));
        applyOutcome(order, payment, gateway.retrieveOutcome(payment.getProviderReference(), simulateDecline));
        return order.getPaymentStatus();
    }

    /** Called by the Stripe webhook with an already-verified event. */
    @Transactional
    public void handleProviderEvent(String providerReference, PaymentGateway.Outcome outcome) {
        paymentRepository.findByProviderReference(providerReference).ifPresentOrElse(payment -> {
            Order order = payment.getOrder();
            if (order.getPaymentStatus() == PaymentStatus.PENDING) {
                applyOutcome(order, payment, outcome);
            }
        }, () -> log.warn("Webhook for unknown payment {}", providerReference));
    }

    private void applyOutcome(Order order, Payment payment, PaymentGateway.Outcome outcome) {
        switch (outcome) {
            case SUCCEEDED -> markPaid(order, payment);
            case FAILED -> markFailed(order, payment);
            case PENDING -> { /* still processing: the webhook will settle it */ }
        }
    }

    private void markPaid(Order order, Payment payment) {
        payment.setStatus(Payment.Status.SUCCEEDED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.getShopOrders().stream()
                .filter(so -> so.getStatus() == OrderStatus.AWAITING_PAYMENT)
                .forEach(so -> so.moveTo(OrderStatus.PENDING, "Payment received", null));
        events.publishEvent(new OrderEvent(OrderEvent.Type.ORDER_PLACED, order.getId(), null));
    }

    /** A declined card cancels the order and gives the reserved stock back. */
    private void markFailed(Order order, Payment payment) {
        payment.setStatus(Payment.Status.FAILED);
        order.getShopOrders().stream()
                .filter(so -> so.getStatus() == OrderStatus.AWAITING_PAYMENT)
                .forEach(so -> lifecycle.transition(so, OrderStatus.CANCELLED, "Payment declined", null));
        order.setPaymentStatus(PaymentStatus.FAILED);
    }
}
