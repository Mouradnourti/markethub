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

    /** Below this trigram word similarity, a name is not considered a typo of the query. */
    public static final double FUZZY_THRESHOLD = 0.5;
    private static final int FUZZY_MIN_LENGTH = 4;

    /**
     * Substring match on name, tagline and description, plus typo tolerance on the name for queries of four
     * characters or more ("hedphones" finds "Headphones"), using PostgreSQL trigrams.
     */
    public static Specification<Product> matchesText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + escapeLike(normalized) + "%";
        return (root, query, cb) -> {
            var name = cb.lower(root.get("name"));
            var substring = cb.or(
                    cb.like(name, pattern, ESCAPE),
                    cb.like(cb.lower(root.get("tagline")), pattern, ESCAPE),
                    cb.like(cb.lower(root.get("description")), pattern, ESCAPE));
            if (normalized.length() < FUZZY_MIN_LENGTH) {
                return substring;
            }
            return cb.or(substring, cb.greaterThan(similarity(cb, normalized, name), FUZZY_THRESHOLD));
        };
    }

    /** Orders by closeness of the name to the query (for the RELEVANCE sort); count queries are left alone. */
    public static Specification<Product> orderedByRelevance(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var name = cb.lower(root.get("name"));
                query.orderBy(
                        cb.desc(cb.selectCase().when(cb.like(name, escapeLike(normalized) + "%", ESCAPE), 1).otherwise(0)),
                        cb.desc(similarity(cb, normalized, name)),
                        cb.desc(root.get("soldCount")),
                        cb.asc(root.get("id")));
            }
            return null;
        };
    }

    private static jakarta.persistence.criteria.Expression<Double> similarity(
            jakarta.persistence.criteria.CriteriaBuilder cb, String text,
            jakarta.persistence.criteria.Expression<String> value) {
        return cb.function("word_similarity", Double.class, cb.literal(text), value);
    }

    private static final char ESCAPE = '!';

    /** Escapes LIKE wildcards with {@code !}, the escape character every catalog LIKE declares. */
    public static String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
