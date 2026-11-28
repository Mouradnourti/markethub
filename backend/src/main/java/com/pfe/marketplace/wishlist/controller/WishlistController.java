package com.pfe.marketplace.wishlist.controller;

import com.pfe.marketplace.security.AuthUser;
import com.pfe.marketplace.wishlist.dto.WishlistDtos.WishlistItemDto;
import com.pfe.marketplace.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wishlist")
@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public List<WishlistItemDto> list(@AuthenticationPrincipal AuthUser user) {
        return wishlistService.list(user.id());
    }

    @Operation(summary = "Ids of the saved products (to draw the hearts on product cards)")
    @GetMapping("/ids")
    public List<Long> ids(@AuthenticationPrincipal AuthUser user) {
        return wishlistService.productIds(user.id());
    }

    @Operation(summary = "Save a product; you are told when it is back in stock or its price drops")
    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId) {
        wishlistService.add(user.id(), productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId) {
        wishlistService.remove(user.id(), productId);
    }
}
