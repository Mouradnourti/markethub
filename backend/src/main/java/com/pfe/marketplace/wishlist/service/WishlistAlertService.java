package com.pfe.marketplace.wishlist.service;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.event.ProductChangedEvent;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.util.MoneyFormat;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.config.EngagementProperties;
import com.pfe.marketplace.inbox.entity.NotificationType;
import com.pfe.marketplace.inbox.service.InboxService;
import com.pfe.marketplace.notification.NotificationClient;
import com.pfe.marketplace.notification.NotificationClient.ProductAlert;
import com.pfe.marketplace.notification.OrderDocument;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.wishlist.entity.WishlistItem;
import com.pfe.marketplace.wishlist.repository.WishlistItemRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells customers who saved a product when it is back in stock or cheaper than when they last heard about it.
 * Runs after the seller's change has committed, on a background thread; an e-mail outage is only logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistAlertService {

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final InboxService inbox;
    private final NotificationClient notificationClient;
    private final AppProperties properties;
    private final EngagementProperties engagement;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProductChanged(ProductChangedEvent event) {
        Product product = productRepository.findWithShopAndCategoryById(event.productId()).orElse(null);
        if (product == null || !product.isPurchasable()) {
            return;
        }
        for (WishlistItem item : wishlistRepository.findByProductIdWithUser(event.productId())) {
            if (event.priceDropped() && product.getPrice().compareTo(item.referencePrice()) < 0 && product.getStock() > 0) {
                alertPriceDrop(item, product);
            } else if (event.backInStock()) {
                alertBackInStock(item.getUser(), product);
            }
        }
    }

    private void alertPriceDrop(WishlistItem item, Product product) {
        BigDecimal before = item.referencePrice();
        item.setLastAlertPrice(product.getPrice());
        String currency = properties.currency();
        inbox.notify(item.getUser().getId(), NotificationType.PRICE_DROP, "Price drop: " + product.getName(),
                "Now %s, was %s when you saved it".formatted(MoneyFormat.format(currency, product.getPrice()),
                        MoneyFormat.format(currency, before)),
                "/products/" + product.getId());
        email("PRICE_DROP", item.getUser(), product, before);
    }

    private void alertBackInStock(User user, Product product) {
        inbox.notify(user.getId(), NotificationType.BACK_IN_STOCK, product.getName() + " is back in stock",
                "Sold by " + product.getShop().getName() + " · " + product.getStock() + " available",
                "/products/" + product.getId());
        email("BACK_IN_STOCK", user, product, null);
    }

    private void email(String type, User user, Product product, BigDecimal previousPrice) {
        if (!properties.notification().enabled()) {
            return;
        }
        try {
            notificationClient.sendProductAlert(type, new OrderDocument.Person(user.getFullName(), user.getEmail()),
                    new ProductAlert(product.getName(), product.getShop().getName(), properties.currency(),
                            product.getPrice(), previousPrice, engagement.link("/products/" + product.getId())));
        } catch (RuntimeException e) {
            log.warn("Could not e-mail the {} alert for product {}: {}", type, product.getId(), e.getMessage());
        }
    }
}
