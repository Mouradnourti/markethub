package com.pfe.marketplace.wishlist.dto;

import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import java.math.BigDecimal;
import java.time.Instant;

public final class WishlistDtos {

    private WishlistDtos() {
    }

    public record WishlistItemDto(ProductSummaryDto product, BigDecimal priceWhenAdded, Instant addedAt) {
    }
}
