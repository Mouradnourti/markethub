package com.pfe.marketplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.catalog.service.ProductService;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ApplicationEventPublisher events;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;
    @Mock com.pfe.marketplace.promotion.service.CouponService couponService;
    @InjectMocks OrderLifecycleService service;

    Product product = Fixtures.product(11, Fixtures.shop(1, ShopStatus.APPROVED), "5.00", 3);

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(cacheManager.getCache(ProductService.PRODUCT_CACHE)).thenReturn(cache);
    }

    @Test
    void cancellingReleasesStockEvictsCacheAndPublishesEvent() {
        ShopOrder parcel = Fixtures.shopOrder(4, OrderStatus.PENDING, PaymentMethod.COD, product);

        service.transition(parcel, OrderStatus.CANCELLED, "changed mind", null);

        assertThat(parcel.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(parcel.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(productRepository).releaseStock(11L, 2);
        verify(cache).evict(11L);
        ArgumentCaptor<OrderEvent> event = ArgumentCaptor.forClass(OrderEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(OrderEvent.Type.ORDER_CANCELLED);
        assertThat(event.getValue().shopOrderId()).isEqualTo(4L);
    }

    @Test
    void confirmingKeepsStockAndPublishesNothing() {
        ShopOrder parcel = Fixtures.shopOrder(4, OrderStatus.PENDING, PaymentMethod.COD, product);

        service.transition(parcel, OrderStatus.CONFIRMED, null, null);

        verify(productRepository, never()).releaseStock(any(), org.mockito.ArgumentMatchers.anyInt());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void deliveringCodMarksOrderPaid() {
        ShopOrder parcel = Fixtures.shopOrder(4, OrderStatus.SHIPPED, PaymentMethod.COD, product);
        when(cacheManager.getCache(ProductService.PRODUCT_CACHE)).thenReturn(null);

        service.transition(parcel, OrderStatus.DELIVERED, null, null);

        assertThat(parcel.getOrder().getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        service.evictProduct(1L); // no cache configured: must not fail
    }
}
