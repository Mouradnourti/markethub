package com.pfe.marketplace.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.order.dto.OrderDtos.PaymentInitDto;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.order.service.OrderLifecycleService;
import com.pfe.marketplace.payment.entity.Payment;
import com.pfe.marketplace.payment.gateway.PaymentGateway;
import com.pfe.marketplace.payment.gateway.SimulatedPaymentGateway;
import com.pfe.marketplace.payment.repository.PaymentRepository;
import com.pfe.marketplace.support.Fixtures;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderLifecycleService lifecycle;
    @Mock ApplicationEventPublisher events;
    PaymentService service;

    ShopOrder parcel = Fixtures.shopOrder(1, OrderStatus.AWAITING_PAYMENT, PaymentMethod.CARD);
    Order order = parcel.getOrder();
    Payment payment = new Payment();

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("eur", null, "key", null, null, null,
                new AppProperties.Stripe("", "", ""), null, null, null);
        service = new PaymentService(new SimulatedPaymentGateway(), paymentRepository, orderRepository, lifecycle,
                events, properties);
        order.setTotal(new BigDecimal("42.00"));
        payment.setOrder(order);
        payment.setProviderReference("mock_pi_1");
        when(orderRepository.findByIdAndCustomerId(order.getId(), 1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findFirstByOrderIdOrderByIdDesc(order.getId())).thenReturn(Optional.of(payment));
    }

    @Test
    void codNeedsNoPaymentIntent() {
        order.setPaymentMethod(PaymentMethod.COD);
        assertThat(service.initiate(order)).isNull();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void cardCheckoutCreatesPendingPayment() {
        PaymentInitDto init = service.initiate(order);

        assertThat(init.provider()).isEqualTo("STRIPE_MOCK");
        assertThat(init.clientSecret()).startsWith("mock_pi_");
        assertThat(init.publishableKey()).isNull();
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("42.00");
        assertThat(saved.getValue().getProvider()).isEqualTo(Payment.Provider.STRIPE_MOCK);
    }

    @Test
    void successfulConfirmationReleasesParcelsToSellers() {
        PaymentStatus status = service.confirm(1L, order.getId(), false);

        assertThat(status).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.SUCCEEDED);
        assertThat(parcel.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(events).publishEvent(any(OrderEvent.class));
    }

    @Test
    void declinedCardCancelsParcels() {
        service.confirm(1L, order.getId(), true);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getStatus()).isEqualTo(Payment.Status.FAILED);
        verify(lifecycle).transition(eq(parcel), eq(OrderStatus.CANCELLED), any(), any());
    }

    @Test
    void confirmationIsIdempotent() {
        order.setPaymentStatus(PaymentStatus.PAID);

        assertThat(service.confirm(1L, order.getId(), false)).isEqualTo(PaymentStatus.PAID);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void codOrderCannotBeConfirmedByCard() {
        order.setPaymentMethod(PaymentMethod.COD);

        assertThatThrownBy(() -> service.confirm(1L, order.getId(), false)).isInstanceOf(BusinessException.class);
    }

    @Test
    void webhookSettlesPendingPaymentAndIgnoresUnknownOnes() {
        when(paymentRepository.findByProviderReference("mock_pi_1")).thenReturn(Optional.of(payment));

        service.handleProviderEvent("mock_pi_1", PaymentGateway.Outcome.SUCCEEDED);
        service.handleProviderEvent("unknown", PaymentGateway.Outcome.SUCCEEDED);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }
}
