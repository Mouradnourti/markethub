package com.pfe.marketplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.ai.service.ReturnRiskService;
import com.pfe.marketplace.cart.entity.CartItem;
import com.pfe.marketplace.cart.repository.CartItemRepository;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.order.dto.OrderDtos.CheckoutRequest;
import com.pfe.marketplace.order.dto.OrderDtos.ShippingAddress;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.mapper.OrderMapper;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.payment.service.PaymentService;
import com.pfe.marketplace.settings.dto.SettingsDtos.PlatformSettingsDto;
import com.pfe.marketplace.settings.service.SettingsService;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.support.Fixtures;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
class CheckoutServiceTest {

    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderRepository orderRepository;
    @Mock UserRepository userRepository;
    @Mock SettingsService settingsService;
    @Mock PaymentService paymentService;
    @Mock ReturnRiskService returnRiskService;
    @Mock com.pfe.marketplace.promotion.service.CouponService couponService;
    @Mock OrderLifecycleService lifecycle;
    @Mock OrderMapper orderMapper;
    @Mock ApplicationEventPublisher events;

    CheckoutService service;
    Shop shopA = Fixtures.shop(1, ShopStatus.APPROVED);
    Shop shopB = Fixtures.shop(2, ShopStatus.APPROVED);

    @BeforeEach
    void setUp() {
        service = new CheckoutService(cartItemRepository, productRepository, orderRepository, userRepository,
                settingsService, paymentService, returnRiskService, couponService, lifecycle, orderMapper, events,
                Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC));
        when(userRepository.getReferenceById(7L)).thenReturn(Fixtures.user(7, Role.CUSTOMER));
        when(settingsService.get()).thenReturn(new PlatformSettingsDto(new BigDecimal("10"), new BigDecimal("4.99"),
                new BigDecimal("50")));
        when(settingsService.shippingFeeFor(any())).thenReturn(new BigDecimal("4.99"));
        when(productRepository.reserveStock(anyLong(), anyInt())).thenReturn(1);
    }

    @Test
    void splitsCartPerShopAndComputesTotalsAndCommission() {
        shopB.setCommissionRate(new BigDecimal("5.00"));
        Product a1 = Fixtures.product(1, shopA, "10.00", 5);
        Product a2 = Fixtures.product(2, shopA, "2.50", 5);
        Product b1 = Fixtures.product(3, shopB, "20.00", 5);
        when(cartItemRepository.findByUserIdWithProduct(7L)).thenReturn(List.of(item(a1, 2), item(b1, 1), item(a2, 4)));
        when(returnRiskService.score(any(), eq(7L))).thenReturn(new BigDecimal("0.1234"));

        service.checkout(7L, request(PaymentMethod.COD));

        Order order = savedOrder();
        assertThat(order.getReference()).startsWith("MH-260315-").hasSize(16);
        assertThat(order.getShopOrders()).hasSize(2);
        assertThat(order.getShopOrders().get(0).getSubtotal()).isEqualByComparingTo("30.00");
        assertThat(order.getShopOrders().get(0).getCommissionAmount()).isEqualByComparingTo("3.00");
        assertThat(order.getShopOrders().get(1).getCommissionAmount()).isEqualByComparingTo("1.00");
        assertThat(order.getSubtotal()).isEqualByComparingTo("50.00");
        assertThat(order.getTotal()).isEqualByComparingTo("54.99");
        assertThat(order.getReturnRiskScore()).isEqualByComparingTo("0.1234");
        assertThat(order.getShopOrders()).allMatch(so -> so.getStatus() == OrderStatus.PENDING);
        verify(cartItemRepository).deleteAllForUser(7L);
        verify(events).publishEvent(any(OrderEvent.class));
    }

    @Test
    void cardOrdersAwaitPaymentAndAreNotAnnouncedYet() {
        Product a1 = Fixtures.product(1, shopA, "10.00", 5);
        when(cartItemRepository.findByUserIdWithProduct(7L)).thenReturn(List.of(item(a1, 1)));

        service.checkout(7L, request(PaymentMethod.CARD));

        Order order = savedOrder();
        assertThat(order.getShopOrders().getFirst().getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(order.getReturnRiskScore()).isNull();
        verify(paymentService).initiate(order);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void emptyCartIsRejected() {
        assertThatThrownBy(() -> service.checkout(7L, request(PaymentMethod.COD)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("empty");
    }

    @Test
    void unavailableProductIsRejected() {
        Product hidden = Fixtures.product(1, shopA, "10.00", 5);
        hidden.setActive(false);
        when(cartItemRepository.findByUserIdWithProduct(7L)).thenReturn(List.of(item(hidden, 1)));

        assertThatThrownBy(() -> service.checkout(7L, request(PaymentMethod.COD)))
                .hasMessageContaining("no longer available");
    }

    @Test
    void failedStockReservationAbortsCheckout() {
        Product a1 = Fixtures.product(1, shopA, "10.00", 1);
        when(cartItemRepository.findByUserIdWithProduct(7L)).thenReturn(List.of(item(a1, 3)));
        when(productRepository.reserveStock(1L, 3)).thenReturn(0);

        assertThatThrownBy(() -> service.checkout(7L, request(PaymentMethod.COD)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Not enough stock");
        verify(orderRepository, never()).save(any());
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CartItem item(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }

    private static CheckoutRequest request(PaymentMethod method) {
        return new CheckoutRequest(method, new ShippingAddress(" Sara ", "0612345678", "1 rue", "Lyon", "69001", "France"));
    }
}
