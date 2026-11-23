package com.pfe.marketplace.catalog.service;

import static com.pfe.marketplace.catalog.repository.ProductSpecifications.inCategory;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.inShop;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.inStock;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.matchesText;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.priceAtLeast;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.priceAtMost;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.ratingAtLeast;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.visibleInCatalog;

import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSearchCriteria;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSort;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.mapper.CatalogMapper;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.NotFoundException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public, read-only catalog queries. */
@Service
@RequiredArgsConstructor
public class ProductService {

    public static final String PRODUCT_CACHE = "product";
    public static final int MAX_PAGE_SIZE = 48;

    private final ProductRepository repository;
    private final CatalogMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> search(ProductSearchCriteria criteria, int page, int size) {
        Specification<Product> spec = Specification.allOf(
                visibleInCatalog(),
                matchesText(criteria.q()),
                inCategory(criteria.categoryId()),
                inShop(criteria.shopId()),
                priceAtLeast(criteria.minPrice()),
                priceAtMost(criteria.maxPrice()),
                ratingAtLeast(criteria.minRating()),
                inStock(criteria.inStock()));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sortOf(criteria.sort()));
        return PageResponse.of(repository.findAll(spec, pageable), mapper::toSummary);
    }

    @Cacheable(value = PRODUCT_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductDto getVisible(Long id) {
        return repository.findWithShopAndCategoryById(id)
                .filter(Product::isPurchasable)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    /** Visible products in the given order of ids (unknown or hidden ids are skipped). */
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> findVisibleByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = repository.findByIdIn(ids).stream()
                .filter(Product::isPurchasable)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductSummaryDto> ordered = new LinkedHashMap<>();
        ids.forEach(id -> {
            Product product = byId.get(id);
            if (product != null) {
                ordered.putIfAbsent(id, mapper.toSummary(product));
            }
        });
        return List.copyOf(ordered.values());
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> popular(int limit) {
        return search(new ProductSearchCriteria(null, null, null, null, null, null, true, ProductSort.POPULAR), 0, limit)
                .content();
    }

    /** Same category, best sellers first, excluding the product itself. */
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> similar(Long productId, int limit) {
        Product product = repository.findWithShopAndCategoryById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        var criteria = new ProductSearchCriteria(null, product.getCategory().getId(), null, null, null, null, true,
                ProductSort.POPULAR);
        return search(criteria, 0, limit + 1).content().stream()
                .filter(p -> !p.id().equals(productId))
                .limit(limit)
                .toList();
    }

    private static Sort sortOf(ProductSort sort) {
        return switch (sort == null ? ProductSort.NEWEST : sort) {
            case PRICE_ASC -> Sort.by("price").ascending().and(Sort.by("id"));
            case PRICE_DESC -> Sort.by("price").descending().and(Sort.by("id"));
            case RATING -> Sort.by("avgRating").descending().and(Sort.by("reviewCount").descending());
            case POPULAR -> Sort.by("soldCount").descending().and(Sort.by("id"));
            case NEWEST -> Sort.by("createdAt").descending().and(Sort.by("id").descending());
        };
    }
}
