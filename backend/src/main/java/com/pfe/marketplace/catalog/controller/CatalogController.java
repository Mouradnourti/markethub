package com.pfe.marketplace.catalog.controller;

import com.pfe.marketplace.catalog.dto.CatalogDtos.CategoryDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSearchCriteria;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSort;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.service.CategoryService;
import com.pfe.marketplace.catalog.service.ProductService;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.settings.dto.SettingsDtos.PublicConfigDto;
import com.pfe.marketplace.settings.service.SettingsService;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopDto;
import com.pfe.marketplace.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Catalog (public)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final ShopService shopService;
    private final SettingsService settingsService;

    @Operation(summary = "Search the catalog with filters, sorting and pagination")
    @GetMapping("/products")
    public PageResponse<ProductSummaryDto> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Long categoryId,
                                                  @RequestParam(required = false) Long shopId,
                                                  @RequestParam(required = false) BigDecimal minPrice,
                                                  @RequestParam(required = false) BigDecimal maxPrice,
                                                  @RequestParam(required = false) BigDecimal minRating,
                                                  @RequestParam(required = false) Boolean inStock,
                                                  @RequestParam(required = false) Boolean onSale,
                                                  @RequestParam(defaultValue = "NEWEST") ProductSort sort,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "12") int size) {
        var criteria = new ProductSearchCriteria(q, categoryId, shopId, minPrice, maxPrice, minRating, inStock, onSale,
                sort);
        return productService.search(criteria, page, size);
    }

    @Operation(summary = "Product details")
    @GetMapping("/products/{id}")
    public ProductDto product(@PathVariable Long id) {
        return productService.getVisible(id);
    }

    @Operation(summary = "Products similar to this one (same category, best sellers)")
    @GetMapping("/products/{id}/similar")
    public List<ProductSummaryDto> similar(@PathVariable Long id, @RequestParam(defaultValue = "6") int limit) {
        return productService.similar(id, Math.clamp(limit, 1, 24));
    }

    @Operation(summary = "Products frequently ordered together with this one (padded with similar products)")
    @GetMapping("/products/{id}/bought-together")
    public List<ProductSummaryDto> boughtTogether(@PathVariable Long id, @RequestParam(defaultValue = "2") int limit) {
        return productService.boughtTogether(id, Math.clamp(limit, 1, 6));
    }

    @Operation(summary = "All categories (flat list with parent ids)")
    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return categoryService.findAll();
    }

    @Operation(summary = "Public shop page")
    @GetMapping("/shops/{slug}")
    public ShopDto shop(@PathVariable String slug) {
        return shopService.getPublicBySlug(slug);
    }

    @Operation(summary = "Public client configuration (currency, payment provider, shipping rules)")
    @GetMapping("/config")
    public PublicConfigDto config() {
        return settingsService.publicConfig();
    }
}
