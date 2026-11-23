package com.pfe.marketplace.catalog.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Catalog API contracts. Grouped in one file because they are small and always change together. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryDto(Long id, String name, String slug, String description, Long parentId)
            implements Serializable {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 1000) String description,
            Long parentId) {
    }

    /** Light projection for grids and search results. */
    public record ProductSummaryDto(
            Long id,
            String name,
            BigDecimal price,
            int stock,
            BigDecimal avgRating,
            int reviewCount,
            int soldCount,
            String imageUrl,
            Long shopId,
            String shopName,
            Long categoryId,
            String categoryName,
            boolean active) implements Serializable {
    }

    public record ProductDto(
            Long id,
            String name,
            String description,
            BigDecimal price,
            int stock,
            boolean active,
            BigDecimal avgRating,
            int reviewCount,
            int soldCount,
            Long categoryId,
            String categoryName,
            Long shopId,
            String shopName,
            String shopSlug,
            List<String> images,
            Instant createdAt) implements Serializable {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 5000) String description,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000000") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Min(0) @Max(1_000_000) int stock,
            @NotNull Long categoryId,
            Boolean active,
            List<@NotBlank @Size(max = 500) String> imageUrls) {
    }

    public record StockUpdateRequest(@Min(0) @Max(1_000_000) int stock) {
    }

    public enum ProductSort {
        NEWEST, PRICE_ASC, PRICE_DESC, RATING, POPULAR
    }

    public record ProductSearchCriteria(
            String q,
            Long categoryId,
            Long shopId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            BigDecimal minRating,
            Boolean inStock,
            ProductSort sort) {
    }
}
