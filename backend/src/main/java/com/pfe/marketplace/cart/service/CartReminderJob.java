package com.pfe.marketplace.cart.service;

import com.pfe.marketplace.cart.entity.CartItem;
import com.pfe.marketplace.cart.repository.CartItemRepository;
import com.pfe.marketplace.common.util.MoneyFormat;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.config.EngagementProperties;
import com.pfe.marketplace.inbox.entity.NotificationType;
import com.pfe.marketplace.inbox.service.InboxService;
import com.pfe.marketplace.notification.NotificationClient;
import com.pfe.marketplace.notification.NotificationClient.CartReminder;
import com.pfe.marketplace.notification.OrderDocument;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reminds customers once about a cart left untouched for a day (configurable). A new addition to the cart
 * re-arms the reminder; nothing is sent twice for the same cart.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartReminderJob {

    private static final int BATCH = 50;

    private final JdbcTemplate jdbc;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final InboxService inbox;
    private final NotificationClient notificationClient;
    private final AppProperties properties;
    private final EngagementProperties engagement;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT3M")
    public void run() {
        if (engagement.cartReminderEnabled()) {
            remindAbandonedCarts();
        }
    }

    /** Returns how many customers were reminded. */
    public int remindAbandonedCarts() {
        Instant cutoff = clock.instant().minus(engagement.cartReminderDelay());
        List<Long> customers = jdbc.queryForList("""
                select u.id from users u join cart_items ci on ci.user_id = u.id
                where u.enabled and u.role = 'CUSTOMER'
                group by u.id, u.cart_reminder_sent_at
                having max(ci.added_at) < ? and (u.cart_reminder_sent_at is null or u.cart_reminder_sent_at < max(ci.added_at))
                order by u.id limit ?
                """, Long.class, Timestamp.from(cutoff), BATCH);
        int reminded = 0;
        for (Long customerId : customers) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> remind(customerId)))) {
                    reminded++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not send the cart reminder of customer {}: {}", customerId, e.getMessage());
            }
        }
        return reminded;
    }

    private boolean remind(Long customerId) {
        List<CartItem> cart = cartItemRepository.findByUserIdWithProduct(customerId).stream()
                .filter(ci -> ci.getProduct().isPurchasable() && ci.getProduct().getStock() > 0)
                .toList();
        jdbc.update("update users set cart_reminder_sent_at = ? where id = ?", Timestamp.from(clock.instant()), customerId);
        if (cart.isEmpty()) {
            return false;
        }
        BigDecimal subtotal = cart.stream()
                .map(ci -> ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int units = cart.stream().mapToInt(CartItem::getQuantity).sum();
        inbox.notify(customerId, NotificationType.CART_REMINDER, "Your cart is waiting",
                "%d item%s · %s, ready when you are".formatted(units, units > 1 ? "s" : "",
                        MoneyFormat.format(properties.currency(), subtotal)), "/cart");
        if (properties.notification().enabled()) {
            User customer = userRepository.findById(customerId).orElseThrow();
            List<OrderDocument.Line> lines = cart.stream().map(ci -> new OrderDocument.Line(ci.getProduct().getName(),
                    ci.getQuantity(), ci.getProduct().getPrice(),
                    ci.getProduct().getPrice().multiply(BigDecimal.valueOf(ci.getQuantity())))).toList();
            notificationClient.sendCartReminder(new OrderDocument.Person(customer.getFullName(), customer.getEmail()),
                    new CartReminder(properties.currency(), subtotal, engagement.link("/cart"), lines));
        }
        return true;
    }
}
