package com.pfe.marketplace.order.service;

import com.pfe.marketplace.cart.entity.CartItem;
import com.pfe.marketplace.cart.repository.CartItemRepository;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.order.dto.OrderDtos.CheckoutRequest;
import com.pfe.marketplace.order.dto.OrderDtos.CheckoutResponse;
import com.pfe.marketplace.order.dto.OrderDtos.PaymentInitDto;
import com.pfe.marketplace.order.dto.OrderDtos.ShippingAddress;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderItem;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.event.OrderEvent;
import com.pfe.marketplace.order.mapper.OrderMapper;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.payment.service.PaymentService;
import com.pfe.marketplace.ai.service.ReturnRiskService;
import com.pfe.marketplace.promotion.service.CouponService;
import com.pfe.marketplace.settings.service.SettingsService;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns the customer's cart into one order split per shop, reserving stock atomically. */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyMMdd").withZone(ZoneOffset.UTC);
    private static final char[] REFERENCE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final PaymentService paymentService;
    private final ReturnRiskService returnRiskService;
    private final CouponService couponService;
    private final OrderLifecycleService lifecycle;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public CheckoutResponse checkout(Long customerId, CheckoutRequest request) {
        List<CartItem> cart = cartItemRepository.findByUserIdWithProduct(customerId);
        if (cart.isEmpty()) {
            throw new BusinessException("Your cart is empty");
        }
        cart.forEach(CheckoutService::ensurePurchasable);

        User customer = userRepository.getReferenceById(customerId);
        Order order = newOrder(customer, request.paymentMethod(), request.shippingAddress());
        OrderStatus initialStatus = request.paymentMethod() == PaymentMethod.COD
                ? OrderStatus.PENDING : OrderStatus.AWAITING_PAYMENT;
        BigDecimal defaultRate = settingsService.get().defaultCommissionRate();
        CouponService.Quote coupon = quoteCoupon(request.couponCode(), customerId, cart);

        groupByShop(cart).forEach((shop, items) -> {
            CouponService.Quote shopCoupon = coupon != null && coupon.coupon().getShop().getId().equals(shop.getId())
                    ? coupon : null;
            order.addShopOrder(buildShopOrder(shop, items, defaultRate, initialStatus, customer, shopCoupon));
        });
        BigDecimal gross = cart.stream().map(CheckoutService::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = coupon == null ? BigDecimal.ZERO : coupon.discount();
        order.setSubtotal(gross);
        order.setDiscountTotal(discount);
        order.setShippingFee(settingsService.shippingFeeFor(gross));
        order.setTotal(gross.subtract(discount).add(order.getShippingFee()));

        if (coupon != null) {
            couponService.redeem(coupon.coupon());
        }
        cart.forEach(this::reserveStock);
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            order.setReturnRiskScore(returnRiskService.score(order, customerId));
        }
        orderRepository.save(order);
        cartItemRepository.deleteAllForUser(customerId);

        PaymentInitDto payment = paymentService.initiate(order);
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            events.publishEvent(new OrderEvent(OrderEvent.Type.ORDER_PLACED, order.getId(), null));
        }
        return new CheckoutResponse(orderMapper.toDto(order, Set.of()), payment);
    }

    private Order newOrder(User customer, PaymentMethod method, ShippingAddress address) {
        Order order = new Order();
        order.setReference(newReference());
        order.setCustomer(customer);
        order.setPaymentMethod(method);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setShippingFullName(address.fullName().trim());
        order.setShippingPhone(address.phone().trim());
        order.setShippingAddressLine(address.addressLine().trim());
        order.setShippingCity(address.city().trim());
        order.setShippingPostalCode(address.postalCode().trim());
        order.setShippingCountry(address.country().trim());
        return order;
    }

    private CouponService.Quote quoteCoupon(String code, Long customerId, List<CartItem> cart) {
        if (code == null || code.isBlank()) {
            return null;
        }
        List<CouponService.Line> lines = cart.stream()
                .map(ci -> new CouponService.Line(ci.getProduct().getShop().getId(), lineTotal(ci))).toList();
        return couponService.quote(code, customerId, lines);
    }

    /** The parcel subtotal is net of the coupon, so commission and seller payout follow the price actually paid. */
    private static ShopOrder buildShopOrder(Shop shop, List<CartItem> items, BigDecimal defaultRate,
                                            OrderStatus status, User customer, CouponService.Quote coupon) {
        ShopOrder shopOrder = new ShopOrder();
        shopOrder.setShop(shop);
        items.forEach(ci -> shopOrder.addItem(toOrderItem(ci)));
        BigDecimal subtotal = shopOrder.getItems().stream().map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (coupon != null) {
            shopOrder.setCouponId(coupon.coupon().getId());
            shopOrder.setCouponCode(coupon.coupon().getCode());
            shopOrder.setDiscountAmount(coupon.discount());
            subtotal = subtotal.subtract(coupon.discount());
        }
        BigDecimal rate = shop.getCommissionRate() != null ? shop.getCommissionRate() : defaultRate;
        shopOrder.setSubtotal(subtotal);
        shopOrder.setCommissionRate(rate);
        shopOrder.setCommissionAmount(subtotal.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        shopOrder.moveTo(status, "Order placed", customer);
        return shopOrder;
    }

    private static OrderItem toOrderItem(CartItem cartItem) {
        Product product = cartItem.getProduct();
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setProductName(product.getName());
        item.setUnitPrice(product.getPrice());
        item.setQuantity(cartItem.getQuantity());
        item.setLineTotal(lineTotal(cartItem));
        return item;
    }

    private static BigDecimal lineTotal(CartItem item) {
        return item.getProduct().getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private void reserveStock(CartItem item) {
        Product product = item.getProduct();
        if (productRepository.reserveStock(product.getId(), item.getQuantity()) == 0) {
            throw new BusinessException("Not enough stock left for \"%s\"".formatted(product.getName()));
        }
        lifecycle.evictProduct(product.getId());
    }

    private static void ensurePurchasable(CartItem item) {
        if (!item.getProduct().isPurchasable()) {
            throw new BusinessException("\"%s\" is no longer available, please remove it from your cart"
                    .formatted(item.getProduct().getName()));
        }
    }

    private static Map<Shop, List<CartItem>> groupByShop(List<CartItem> cart) {
        return cart.stream().collect(Collectors.groupingBy(ci -> ci.getProduct().getShop(),
                LinkedHashMap::new, Collectors.toList()));
    }

    private String newReference() {
        StringBuilder suffix = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            suffix.append(REFERENCE_CHARS[random.nextInt(REFERENCE_CHARS.length)]);
        }
        return "MH-" + REFERENCE_DATE.format(clock.instant()) + "-" + suffix;
    }
}
