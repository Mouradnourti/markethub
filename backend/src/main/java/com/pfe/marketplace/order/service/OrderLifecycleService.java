package com.pfe.marketplace.order.service;

import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.catalog.service.ProductService;
import com.pfe.marketplace.order.entity.OrderItem;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.promotion.service.CouponService;
import com.pfe.marketplace.user.entity.User;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a status change to a shop order with all its side effects: audit trail, stock release,
 * payment status, cache eviction and domain event. Callers are responsible for checking that the
 * transition is allowed for their actor.
 */
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private static final Map<OrderStatus, OrderEvent.Type> EVENTS = Map.of(
            OrderStatus.SHIPPED, OrderEvent.Type.ORDER_SHIPPED,
            OrderStatus.DELIVERED, OrderEvent.Type.ORDER_DELIVERED,
            OrderStatus.CANCELLED, OrderEvent.Type.ORDER_CANCELLED,
            OrderStatus.RETURNED, OrderEvent.Type.ORDER_RETURNED);

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;
    private final CacheManager cacheManager;
    private final CouponService couponService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(ShopOrder shopOrder, OrderStatus next, String note, User actor) {
        shopOrder.moveTo(next, note, actor);
        shopOrder.getOrder().recomputePaymentStatus();
        if (next.releasesStock()) {
            shopOrder.getItems().forEach(this::releaseStock);
        }
        if (next == OrderStatus.CANCELLED) {
            couponService.release(shopOrder.getCouponId());
        }
        Optional.ofNullable(EVENTS.get(next)).ifPresent(type ->
                events.publishEvent(new OrderEvent(type, shopOrder.getOrder().getId(), shopOrder.getId())));
    }

    private void releaseStock(OrderItem item) {
        Long productId = item.getProduct().getId();
        productRepository.releaseStock(productId, item.getQuantity());
        evictProduct(productId);
    }

    void evictProduct(Long productId) {
        Cache cache = cacheManager.getCache(ProductService.PRODUCT_CACHE);
        if (cache != null) {
            cache.evict(productId);
        }
    }
}
