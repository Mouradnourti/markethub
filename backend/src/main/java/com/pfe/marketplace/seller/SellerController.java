package com.pfe.marketplace.seller;

import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductRequest;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.dto.CatalogDtos.StockUpdateRequest;
import com.pfe.marketplace.catalog.service.SellerProductService;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.order.dto.OrderDtos.SellerOrderDto;
import com.pfe.marketplace.order.dto.OrderDtos.UpdateShopOrderStatusRequest;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.service.SellerOrderService;
import com.pfe.marketplace.security.AuthUser;
import com.pfe.marketplace.shop.dto.ShopDtos.ShopDto;
import com.pfe.marketplace.shop.dto.ShopDtos.UpdateShopRequest;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.stats.dto.StatsDtos.SellerAnalyticsDto;
import com.pfe.marketplace.stats.dto.StatsDtos.SellerStatsDto;
import com.pfe.marketplace.stats.service.SellerAnalyticsService;
import com.pfe.marketplace.stats.service.SellerStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Seller back-office. URL rules restrict /api/seller/** to ROLE_SELLER; services scope everything to the seller's shop. */
@Tag(name = "Seller")
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ShopService shopService;
    private final SellerProductService productService;
    private final SellerOrderService orderService;
    private final SellerStatsService statsService;
    private final SellerAnalyticsService analyticsService;

    @GetMapping("/shop")
    public ShopDto shop(@AuthenticationPrincipal AuthUser user) {
        return shopService.getOwnShop(user.id());
    }

    @PutMapping("/shop")
    public ShopDto updateShop(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody UpdateShopRequest request) {
        return shopService.updateOwnShop(user.id(), request);
    }

    @GetMapping("/products")
    public PageResponse<ProductSummaryDto> products(@AuthenticationPrincipal AuthUser user,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return productService.list(user.id(), page, size);
    }

    @GetMapping("/products/{id}")
    public ProductDto product(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return productService.get(user.id(), id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ProductRequest request) {
        return productService.create(user.id(), request);
    }

    @PutMapping("/products/{id}")
    public ProductDto update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                             @Valid @RequestBody ProductRequest request) {
        return productService.update(user.id(), id, request);
    }

    @PatchMapping("/products/{id}/stock")
    public ProductDto updateStock(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody StockUpdateRequest request) {
        return productService.updateStock(user.id(), id, request.stock());
    }

    @Operation(summary = "Upload a product image (JPEG, PNG, WebP, GIF, max 5 MB)")
    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductDto uploadImage(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @RequestPart("file") MultipartFile file) {
        return productService.addImage(user.id(), id, file);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        productService.delete(user.id(), id);
    }

    @GetMapping("/orders")
    public PageResponse<SellerOrderDto> orders(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) OrderStatus status,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return orderService.list(user.id(), status, page, size);
    }

    @GetMapping("/orders/{id}")
    public SellerOrderDto order(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.get(user.id(), id);
    }

    @Operation(summary = "Move an order to CONFIRMED, SHIPPED, DELIVERED, RETURNED or CANCELLED")
    @PatchMapping("/orders/{id}/status")
    public SellerOrderDto updateOrderStatus(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                            @Valid @RequestBody UpdateShopOrderStatusRequest request) {
        return orderService.updateStatus(user.id(), id, request);
    }

    @Operation(summary = "Views, conversion, revenue and returns per day and per product over the last N days")
    @GetMapping("/analytics")
    public SellerAnalyticsDto analytics(@AuthenticationPrincipal AuthUser user,
                                        @RequestParam(defaultValue = "30") int days) {
        return analyticsService.forSeller(user.id(), days);
    }

    @GetMapping("/stats")
    public SellerStatsDto stats(@AuthenticationPrincipal AuthUser user) {
        return statsService.forSeller(user.id());
    }
}
