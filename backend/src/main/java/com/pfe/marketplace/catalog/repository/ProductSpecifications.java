package com.pfe.marketplace.catalog.repository;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.shop.entity.ShopStatus;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable catalog filters. Each returns null when its parameter is absent (= no filter). */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> visibleInCatalog() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.equal(root.get("shop").get("status"), ShopStatus.APPROVED));
    }

    public static Specification<Product> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String pattern = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    /** Matches the category itself and its direct sub-categories. */
    public static Specification<Product> inCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> {
            var category = root.join("category");
            return cb.or(cb.equal(category.get("id"), categoryId),
                    cb.equal(category.get("parent").get("id"), categoryId));
        };
    }

    public static Specification<Product> inShop(Long shopId) {
        return shopId == null ? null : (root, query, cb) -> cb.equal(root.get("shop").get("id"), shopId);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return min == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> ratingAtLeast(BigDecimal minRating) {
        return minRating == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("avgRating"), minRating);
    }

    public static Specification<Product> onSale(Boolean onSaleOnly) {
        return Boolean.TRUE.equals(onSaleOnly) ? (root, query, cb) -> cb.isNotNull(root.get("compareAtPrice")) : null;
    }

    public static Specification<Product> inStock(Boolean inStockOnly) {
        return Boolean.TRUE.equals(inStockOnly) ? (root, query, cb) -> cb.greaterThan(root.get("stock"), 0) : null;
    }
}
