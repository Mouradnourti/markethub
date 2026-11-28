package com.pfe.marketplace.wishlist.service;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.mapper.CatalogMapper;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.user.repository.UserRepository;
import com.pfe.marketplace.wishlist.dto.WishlistDtos.WishlistItemDto;
import com.pfe.marketplace.wishlist.entity.WishlistItem;
import com.pfe.marketplace.wishlist.repository.WishlistItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saved products. The price at save time is kept so the customer can be told when it drops. */
@Service
@RequiredArgsConstructor
public class WishlistService {

    public static final int MAX_ITEMS = 200;

    private final WishlistItemRepository repository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CatalogMapper mapper;

    @Transactional(readOnly = true)
    public List<WishlistItemDto> list(Long userId) {
        return repository.findByUserIdWithProduct(userId).stream()
                .filter(item -> item.getProduct().isPurchasable())
                .map(item -> new WishlistItemDto(mapper.toSummary(item.getProduct()), item.getPriceWhenAdded(),
                        item.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> productIds(Long userId) {
        return repository.findProductIds(userId);
    }

    /** Idempotent: saving an already saved product changes nothing. */
    @Transactional
    public void add(Long userId, Long productId) {
        if (repository.findByUserIdAndProductId(userId, productId).isPresent()) {
            return;
        }
        Product product = productRepository.findWithShopAndCategoryById(productId)
                .filter(Product::isPurchasable)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        if (repository.findProductIds(userId).size() >= MAX_ITEMS) {
            throw new BusinessException("Your wishlist is full (" + MAX_ITEMS + " products)");
        }
        WishlistItem item = new WishlistItem();
        item.setUser(userRepository.getReferenceById(userId));
        item.setProduct(product);
        item.setPriceWhenAdded(product.getPrice());
        repository.save(item);
    }

    @Transactional
    public void remove(Long userId, Long productId) {
        repository.deleteItem(userId, productId);
    }
}
