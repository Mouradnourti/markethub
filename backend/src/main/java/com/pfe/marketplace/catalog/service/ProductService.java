package com.pfe.marketplace.catalog.service;

import static com.pfe.marketplace.catalog.repository.ProductSpecifications.inCategory;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.inShop;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.inStock;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.matchesText;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.onSale;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.orderedByRelevance;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.priceAtLeast;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.priceAtMost;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.ratingAtLeast;
import static com.pfe.marketplace.catalog.repository.ProductSpecifications.visibleInCatalog;

import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.FacetCount;
import com.pfe.marketplace.catalog.dto.CatalogDtos.FacetsDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.SuggestionsDto;
import com.pfe.marketplace.catalog.repository.CategoryRepository;
import com.pfe.marketplace.catalog.repository.ProductSpecifications;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.util.Locale;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSearchCriteria;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSort;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.mapper.CatalogMapper;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.NotFoundException;
import java.util.ArrayList;
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
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> search(ProductSearchCriteria criteria, int page, int size) {
        boolean byRelevance = criteria.sort() == ProductSort.RELEVANCE && criteria.q() != null && !criteria.q().isBlank();
        Specification<Product> spec = Specification.allOf(filters(criteria, true),
                byRelevance ? orderedByRelevance(criteria.q()) : null);
        Sort sort = byRelevance ? Sort.unsorted() : sortOf(criteria.sort());
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort);
        return PageResponse.of(repository.findAll(spec, pageable), mapper::toSummary);
    }

    /** Instant results while typing: up to {@code limit} products (typos tolerated) and three categories. */
    @Transactional(readOnly = true)
    public SuggestionsDto suggest(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return new SuggestionsDto(List.of(), List.of());
        }
        String escaped = ProductSpecifications.escapeLike(q);
        List<Long> ids = repository.suggestIds(q, "%" + escaped + "%", escaped + "%",
                ProductSpecifications.FUZZY_THRESHOLD - 0.05, limit);
        List<CategoryDto> categories = categoryRepository.findAll().stream()
                .filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(q))
                .limit(3)
                .map(mapper::toDto)
                .toList();
        return new SuggestionsDto(findVisibleByIds(ids), categories);
    }

    /** Counts for the catalog filter sidebar, consistent with {@link #search}. */
    @Transactional(readOnly = true)
    public FacetsDto facets(ProductSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Object[]> byCategory = cb.createQuery(Object[].class);
        Root<Product> root = byCategory.from(Product.class);
        Predicate withoutCategory = filters(criteria, false).toPredicate(root, byCategory, cb);
        var categoryId = root.get("category").get("id");
        byCategory.multiselect(categoryId, cb.count(root)).where(withoutCategory).groupBy(categoryId);
        List<FacetCount> categories = entityManager.createQuery(byCategory).getResultList().stream()
                .map(row -> new FacetCount((Long) row[0], (Long) row[1]))
                .toList();

        CriteriaQuery<Object[]> totals = cb.createQuery(Object[].class);
        Root<Product> all = totals.from(Product.class);
        Predicate everything = filters(criteria, true).toPredicate(all, totals, cb);
        totals.multiselect(
                cb.count(all),
                cb.sum(cb.<Integer>selectCase().when(cb.greaterThan(all.get("stock"), 0), 1).otherwise(0)),
                cb.sum(cb.<Integer>selectCase().when(cb.isNotNull(all.get("compareAtPrice")), 1).otherwise(0)),
                cb.min(all.get("price")),
                cb.max(all.get("price"))).where(everything);
        Object[] row = entityManager.createQuery(totals).getSingleResult();
        return new FacetsDto(toLong(row[0]), toLong(row[1]), toLong(row[2]), (BigDecimal) row[3], (BigDecimal) row[4],
                categories);
    }

    private static long toLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Specification<Product> filters(ProductSearchCriteria criteria, boolean withCategory) {
        return Specification.allOf(
                visibleInCatalog(),
                matchesText(criteria.q()),
                withCategory ? inCategory(criteria.categoryId()) : null,
                inShop(criteria.shopId()),
                priceAtLeast(criteria.minPrice()),
                priceAtMost(criteria.maxPrice()),
                ratingAtLeast(criteria.minRating()),
                inStock(criteria.inStock()),
                onSale(criteria.onSale()));
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
        return search(new ProductSearchCriteria(null, null, null, null, null, null, true, null, ProductSort.POPULAR), 0, limit)
                .content();
    }

    /** Same category, best sellers first, excluding the product itself. */
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> similar(Long productId, int limit) {
        Product product = repository.findWithShopAndCategoryById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        var criteria = new ProductSearchCriteria(null, product.getCategory().getId(), null, null, null, null, true,
                null, ProductSort.POPULAR);
        return search(criteria, 0, limit + 1).content().stream()
                .filter(p -> !p.id().equals(productId))
                .limit(limit)
                .toList();
    }

    /**
     * Products most often ordered together with this one (same checkout), padded with similar products
     * when the order history is too thin.
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> boughtTogether(Long productId, int limit) {
        if (!repository.existsById(productId)) {
            throw new NotFoundException("Product", productId);
        }
        List<Long> ids = repository.findBoughtTogetherIds(productId, limit * 3);
        List<ProductSummaryDto> result = new ArrayList<>(findVisibleByIds(ids).stream()
                .filter(p -> p.stock() > 0)
                .limit(limit)
                .toList());
        if (result.size() < limit) {
            similar(productId, limit * 2).stream()
                    .filter(p -> result.stream().noneMatch(r -> r.id().equals(p.id())))
                    .limit(limit - result.size())
                    .forEach(result::add);
        }
        return result;
    }

    private static Sort sortOf(ProductSort sort) {
        return switch (sort == null ? ProductSort.NEWEST : sort) {
            case PRICE_ASC -> Sort.by("price").ascending().and(Sort.by("id"));
            case PRICE_DESC -> Sort.by("price").descending().and(Sort.by("id"));
            case RATING -> Sort.by("avgRating").descending().and(Sort.by("reviewCount").descending());
            case POPULAR -> Sort.by("soldCount").descending().and(Sort.by("id"));
            case NEWEST, RELEVANCE -> Sort.by("createdAt").descending().and(Sort.by("id").descending());
        };
    }
}
