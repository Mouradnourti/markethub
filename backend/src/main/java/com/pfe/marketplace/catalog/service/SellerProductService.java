package com.pfe.marketplace.catalog.service;

import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductRequest;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.event.ProductChangedEvent;
import com.pfe.marketplace.catalog.mapper.CatalogMapper;
import com.pfe.marketplace.catalog.repository.CategoryRepository;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Product management for the seller who owns the shop. Every method is scoped to that shop. */
@Service
@RequiredArgsConstructor
public class SellerProductService {

    private static final int MAX_IMAGES = 8;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ShopService shopService;
    private final ImageStorageService imageStorage;
    private final CatalogMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryDto> list(Long sellerId, int page, int size) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        var pageable = PageRequest.of(page, Math.clamp(size, 1, 100), Sort.by("createdAt").descending());
        return PageResponse.of(productRepository.findByShopId(shop.getId(), pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public ProductDto get(Long sellerId, Long productId) {
        return mapper.toDto(findOwned(sellerId, productId));
    }

    @Transactional
    public ProductDto create(Long sellerId, ProductRequest request) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        Product product = new Product();
        product.setShop(shop);
        apply(product, request);
        return mapper.toDto(productRepository.save(product));
    }

    @CacheEvict(value = ProductService.PRODUCT_CACHE, key = "#productId")
    @Transactional
    public ProductDto update(Long sellerId, Long productId, ProductRequest request) {
        Product product = findOwned(sellerId, productId);
        int previousStock = product.getStock();
        BigDecimal previousPrice = product.getPrice();
        apply(product, request);
        publishChange(product, previousStock, previousPrice);
        return mapper.toDto(product);
    }

    @CacheEvict(value = ProductService.PRODUCT_CACHE, key = "#productId")
    @Transactional
    public ProductDto updateStock(Long sellerId, Long productId, int stock) {
        Product product = findOwned(sellerId, productId);
        int previousStock = product.getStock();
        product.setStock(stock);
        publishChange(product, previousStock, product.getPrice());
        return mapper.toDto(product);
    }

    @CacheEvict(value = ProductService.PRODUCT_CACHE, key = "#productId")
    @Transactional
    public ProductDto addImage(Long sellerId, Long productId, MultipartFile file) {
        Product product = findOwned(sellerId, productId);
        if (product.getImages().size() >= MAX_IMAGES) {
            throw new BusinessException("A product can have at most " + MAX_IMAGES + " images");
        }
        product.addImage(imageStorage.storeProductImage(file));
        return mapper.toDto(product);
    }

    /** Products that were already ordered are deactivated instead of deleted, to keep order history intact. */
    @CacheEvict(value = ProductService.PRODUCT_CACHE, key = "#productId")
    @Transactional
    public void delete(Long sellerId, Long productId) {
        Product product = findOwned(sellerId, productId);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("This product has orders; deactivate it instead of deleting it");
        }
    }

    private Product findOwned(Long sellerId, Long productId) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        return productRepository.findByIdAndShopId(productId, shop.getId())
                .orElseThrow(() -> new NotFoundException("Product", productId));
    }

    private void publishChange(Product product, int previousStock, BigDecimal previousPrice) {
        var event = new ProductChangedEvent(product.getId(), previousStock, product.getStock(), previousPrice,
                product.getPrice());
        if (product.isActive() && (event.backInStock() || event.priceDropped())) {
            events.publishEvent(event);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void apply(Product product, ProductRequest request) {
        BigDecimal price = request.price().setScale(2, RoundingMode.HALF_UP);
        BigDecimal compareAt = request.compareAtPrice() == null
                ? null : request.compareAtPrice().setScale(2, RoundingMode.HALF_UP);
        if (compareAt != null && compareAt.compareTo(price) <= 0) {
            throw new BusinessException("The compare-at price must be higher than the selling price");
        }
        product.setName(request.name().trim());
        product.setTagline(blankToNull(request.tagline()));
        product.setDescription(request.description());
        product.setHighlights(request.highlights() == null ? new ArrayList<>()
                : request.highlights().stream().map(String::trim).collect(Collectors.toCollection(ArrayList::new)));
        product.setSpecs(request.specs() == null ? new ArrayList<>()
                : request.specs().stream().map(mapper::toEntity).collect(Collectors.toCollection(ArrayList::new)));
        product.setPrice(price);
        product.setCompareAtPrice(compareAt);
        product.setStock(request.stock());
        product.setActive(request.active() == null || request.active());
        product.setCategory(categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new NotFoundException("Category", request.categoryId())));
        List<String> urls = request.imageUrls();
        if (urls != null) {
            if (urls.size() > MAX_IMAGES) {
                throw new BusinessException("A product can have at most " + MAX_IMAGES + " images");
            }
            product.getImages().clear();
            urls.forEach(product::addImage);
        }
    }
}
