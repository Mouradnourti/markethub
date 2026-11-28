package com.pfe.marketplace.cart.controller;

import com.pfe.marketplace.cart.dto.CartDtos.AddCartItemRequest;
import com.pfe.marketplace.cart.dto.CartDtos.CartDto;
import com.pfe.marketplace.cart.dto.CartDtos.UpdateCartItemRequest;
import com.pfe.marketplace.cart.service.CartService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cart")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "The cart; pass a discount code to preview its effect (an invalid code returns 422)")
    @GetMapping
    public CartDto get(@AuthenticationPrincipal AuthUser user, @RequestParam(required = false) String coupon) {
        return cartService.get(user.id(), coupon);
    }

    @PostMapping("/items")
    public CartDto add(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AddCartItemRequest request) {
        return cartService.addItem(user.id(), request);
    }

    @PutMapping("/items/{productId}")
    public CartDto update(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId,
                          @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(user.id(), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartDto remove(@AuthenticationPrincipal AuthUser user, @PathVariable Long productId) {
        return cartService.removeItem(user.id(), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthUser user) {
        cartService.clear(user.id());
    }
}
