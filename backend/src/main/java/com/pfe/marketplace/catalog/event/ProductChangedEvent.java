package com.pfe.marketplace.catalog.event;

import java.math.BigDecimal;

/** Published when a seller changes a product's price or stock; wishlist alerts listen to it after commit. */
public record ProductChangedEvent(Long productId, int previousStock, int stock, BigDecimal previousPrice,
                                  BigDecimal price) {

    public boolean backInStock() {
        return previousStock == 0 && stock > 0;
    }

    public boolean priceDropped() {
        return price.compareTo(previousPrice) < 0;
    }
}
