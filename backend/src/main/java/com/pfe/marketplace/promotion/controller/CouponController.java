package com.pfe.marketplace.promotion.controller;

import com.pfe.marketplace.promotion.dto.PromotionDtos.CouponDto;
import com.pfe.marketplace.promotion.dto.PromotionDtos.CouponRequest;
import com.pfe.marketplace.promotion.dto.PromotionDtos.OfferDto;
import com.pfe.marketplace.promotion.service.CouponService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Promotions")
@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "Featured discount codes of a shop that are valid right now")
    @GetMapping("/api/shops/{shopId}/offers")
    public List<OfferDto> offers(@PathVariable Long shopId) {
        return couponService.offers(shopId);
    }

    @GetMapping("/api/seller/coupons")
    public List<CouponDto> list(@AuthenticationPrincipal AuthUser user) {
        return couponService.list(user.id());
    }

    @Operation(summary = "Create a discount code for the seller's shop")
    @PostMapping("/api/seller/coupons")
    @ResponseStatus(HttpStatus.CREATED)
    public CouponDto create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CouponRequest request) {
        return couponService.create(user.id(), request);
    }

    @PutMapping("/api/seller/coupons/{id}")
    public CouponDto update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                            @Valid @RequestBody CouponRequest request) {
        return couponService.update(user.id(), id, request);
    }

    @DeleteMapping("/api/seller/coupons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        couponService.delete(user.id(), id);
    }
}
