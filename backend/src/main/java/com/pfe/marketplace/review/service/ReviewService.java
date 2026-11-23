package com.pfe.marketplace.review.service;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.catalog.service.ProductService;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewEligibilityDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewRequest;
import com.pfe.marketplace.review.entity.Review;
import com.pfe.marketplace.review.repository.ReviewRepository;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Only customers who received a product may review it, once. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> list(Long productId, int page, int size) {
        var pageable = PageRequest.of(page, Math.clamp(size, 1, 50), Sort.by("createdAt").descending());
        return PageResponse.of(reviewRepository.findByProductId(productId, pageable), ReviewService::toDto);
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityDto eligibility(Long userId, Long productId) {
        if (userId == null) {
            return new ReviewEligibilityDto(false, "Sign in to review this product");
        }
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            return new ReviewEligibilityDto(false, "You already reviewed this product");
        }
        if (!shopOrderRepository.customerReceivedProduct(userId, productId)) {
            return new ReviewEligibilityDto(false, "Only customers who received this product can review it");
        }
        return new ReviewEligibilityDto(true, null);
    }

    @CacheEvict(value = ProductService.PRODUCT_CACHE, key = "#productId")
    @Transactional
    public ReviewDto create(Long userId, Long productId, ReviewRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product", productId));
        if (reviewRepository.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("You already reviewed this product");
        }
        if (!shopOrderRepository.customerReceivedProduct(userId, productId)) {
            throw new BusinessException("Only customers who received this product can review it");
        }
        Review review = new Review();
        review.setProduct(product);
        review.setUser(userRepository.getReferenceById(userId));
        review.setRating(request.rating());
        review.setComment(request.comment() == null ? null : request.comment().trim());
        reviewRepository.saveAndFlush(review);
        refreshRating(productId);
        return toDto(reviewRepository.findById(review.getId()).orElseThrow());
    }

    private void refreshRating(Long productId) {
        BigDecimal average = BigDecimal.valueOf(reviewRepository.averageRating(productId)).setScale(2, RoundingMode.HALF_UP);
        productRepository.updateRating(productId, average, (int) reviewRepository.countByProductId(productId));
    }

    private static ReviewDto toDto(Review review) {
        User author = review.getUser();
        String name = author.getFirstName() + " " + author.getLastName().charAt(0) + ".";
        return new ReviewDto(review.getId(), review.getRating(), review.getComment(), name, review.getCreatedAt());
    }
}
