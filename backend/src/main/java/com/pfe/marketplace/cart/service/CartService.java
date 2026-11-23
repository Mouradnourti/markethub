package com.pfe.marketplace.cart.service;

import com.pfe.marketplace.cart.dto.CartDtos.AddCartItemRequest;
import com.pfe.marketplace.cart.dto.CartDtos.CartDto;
import com.pfe.marketplace.cart.dto.CartDtos.CartItemDto;
import com.pfe.marketplace.cart.entity.CartItem;
import com.pfe.marketplace.cart.repository.CartItemRepository;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.settings.service.SettingsService;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {

    public static final int MAX_QUANTITY = 99;

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public CartDto get(Long userId) {
        List<CartItemDto> items = cartItemRepository.findByUserIdWithProduct(userId).stream().map(CartService::toDto).toList();
        BigDecimal subtotal = items.stream().filter(CartItemDto::available)
                .map(CartItemDto::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = settingsService.shippingFeeFor(subtotal);
        int count = items.stream().mapToInt(CartItemDto::quantity).sum();
        return new CartDto(items, count, subtotal, shipping, subtotal.add(shipping));
    }

    /** Adds to the existing line if the product is already in the cart. */
    @Transactional
    public CartDto addItem(Long userId, AddCartItemRequest request) {
        Product product = productRepository.findWithShopAndCategoryById(request.productId())
                .filter(Product::isPurchasable)
                .orElseThrow(() -> new NotFoundException("Product", request.productId()));
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, product.getId()).orElseGet(() -> {
            CartItem created = new CartItem();
            created.setUser(userRepository.getReferenceById(userId));
            created.setProduct(product);
            return created;
        });
        int quantity = Math.min(item.getQuantity() + request.quantity(), MAX_QUANTITY);
        ensureStock(product, quantity);
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return get(userId);
    }

    @Transactional
    public CartDto updateQuantity(Long userId, Long productId, int quantity) {
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new NotFoundException("This product is not in your cart"));
        ensureStock(item.getProduct(), quantity);
        item.setQuantity(quantity);
        return get(userId);
    }

    @Transactional
    public CartDto removeItem(Long userId, Long productId) {
        cartItemRepository.deleteItem(userId, productId);
        return get(userId);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteAllForUser(userId);
    }

    private static void ensureStock(Product product, int quantity) {
        if (quantity > product.getStock()) {
            throw new BusinessException("Only %d unit(s) of \"%s\" left in stock".formatted(product.getStock(), product.getName()));
        }
    }

    private static CartItemDto toDto(CartItem item) {
        Product product = item.getProduct();
        boolean available = product.isPurchasable() && product.getStock() >= item.getQuantity();
        String image = product.getImages().isEmpty() ? null : product.getImages().getFirst().getUrl();
        return new CartItemDto(product.getId(), product.getName(), image, product.getShop().getId(),
                product.getShop().getName(), product.getPrice(), item.getQuantity(), product.getStock(), available,
                product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
    }
}
