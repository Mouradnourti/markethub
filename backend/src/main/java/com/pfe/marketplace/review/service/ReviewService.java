package com.pfe.marketplace.review.service;

import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.catalog.service.ImageStorageService;
import com.pfe.marketplace.catalog.service.ProductService;
import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.common.exception.ForbiddenException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.inbox.entity.NotificationType;
import com.pfe.marketplace.inbox.service.InboxService;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewEligibilityDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewRequest;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewSort;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewSummaryDto;
import com.pfe.marketplace.review.dto.ReviewDtos.SellerReviewDto;
import com.pfe.marketplace.review.entity.Review;
import com.pfe.marketplace.review.repository.ReviewRepository;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Only customers who received a product may review it, once. Other customers can mark reviews as helpful,
 * the seller can answer publicly, and the author can add up to {@value #MAX_PHOTOS} photos.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    public static final int MAX_PHOTOS = 3;

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final UserRepository userRepository;
    private final ShopService shopService;
    private final ImageStorageService imageStorage;
    private final InboxService inbox;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> list(Long productId, int page, int size) {
        return list(productId, null, ReviewSort.RECENT, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> list(Long productId, Long viewerId, ReviewSort sort, Integer rating, int page,
                                        int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), sortOf(sort));
        Page<Review> reviews = rating == null
                ? reviewRepository.findByProductId(productId, pageable)
                : reviewRepository.findByProductIdAndRating(productId, rating, pageable);
        Set<Long> voted = viewerId == null || reviews.isEmpty() ? Set.of()
                : new HashSet<>(reviewRepository.findVotedReviewIds(viewerId,
                        reviews.getContent().stream().map(Review::getId).toList()));
        return PageResponse.of(reviews, r -> toDto(r, voted.contains(r.getId()), viewerId));
    }

    @Transactional(readOnly = true)
    public ReviewSummaryDto summary(Long productId) {
        long[] counts = new long[5];
        for (Object[] row : reviewRepository.countByRating(productId)) {
            counts[((Number) row[0]).intValue() - 1] = ((Number) row[1]).longValue();
        }
        long total = Arrays.stream(counts).sum();
        BigDecimal average = BigDecimal.valueOf(reviewRepository.averageRating(productId)).setScale(2, RoundingMode.HALF_UP);
        return new ReviewSummaryDto(average, total, Arrays.stream(counts).boxed().toList(),
                reviewRepository.countWithPhotos(productId));
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
        return toDto(reviewRepository.findWithUserById(review.getId()).orElseThrow(), false, userId);
    }

    /** Marks a review as helpful (idempotent). Authors cannot vote for their own review. */
    @Transactional
    public ReviewDto voteHelpful(Long userId, Long productId, Long reviewId, boolean helpful) {
        Review review = findOnProduct(productId, reviewId);
        if (review.getUser().getId().equals(userId)) {
            throw new BusinessException("You cannot vote for your own review");
        }
        if (helpful) {
            reviewRepository.addVote(reviewId, userId);
        } else {
            reviewRepository.removeVote(reviewId, userId);
        }
        reviewRepository.refreshHelpfulCount(reviewId);
        return toDto(reviewRepository.findWithUserById(reviewId).orElseThrow(), helpful, userId);
    }

    @Transactional
    public ReviewDto addPhoto(Long userId, Long productId, Long reviewId, MultipartFile file) {
        Review review = findOnProduct(productId, reviewId);
        if (!review.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Only the author can add photos to this review");
        }
        if (review.getPhotoUrls().size() >= MAX_PHOTOS) {
            throw new BusinessException("A review can have at most " + MAX_PHOTOS + " photos");
        }
        List<String> photos = new ArrayList<>(review.getPhotoUrls());
        photos.add(imageStorage.storeImage("reviews", file));
        review.setPhotoUrls(photos);
        return toDto(review, false, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<SellerReviewDto> listForSeller(Long sellerId, boolean unansweredOnly, int page, int size) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 50), Sort.by("createdAt").descending());
        return PageResponse.of(reviewRepository.findForShop(shop.getId(), unansweredOnly, pageable),
                ReviewService::toSellerDto);
    }

    /** Public answer of the seller; the review's author is notified. Replying again edits the answer. */
    @Transactional
    public SellerReviewDto reply(Long sellerId, Long reviewId, String reply) {
        Review review = findForSeller(sellerId, reviewId);
        boolean first = review.getSellerReply() == null;
        review.setSellerReply(reply.trim());
        review.setSellerRepliedAt(clock.instant());
        if (first) {
            Product product = review.getProduct();
            inbox.notify(review.getUser().getId(), NotificationType.REVIEW_REPLY,
                    product.getShop().getName() + " replied to your review",
                    "On " + product.getName(), "/products/" + product.getId() + "#reviews");
        }
        return toSellerDto(review);
    }

    @Transactional
    public SellerReviewDto deleteReply(Long sellerId, Long reviewId) {
        Review review = findForSeller(sellerId, reviewId);
        review.setSellerReply(null);
        review.setSellerRepliedAt(null);
        return toSellerDto(review);
    }

    private Review findForSeller(Long sellerId, Long reviewId) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        return reviewRepository.findWithUserById(reviewId)
                .filter(r -> r.getProduct().getShop().getId().equals(shop.getId()))
                .orElseThrow(() -> new NotFoundException("Review", reviewId));
    }

    private Review findOnProduct(Long productId, Long reviewId) {
        return reviewRepository.findWithUserById(reviewId)
                .filter(r -> r.getProduct().getId().equals(productId))
                .orElseThrow(() -> new NotFoundException("Review", reviewId));
    }

    private void refreshRating(Long productId) {
        BigDecimal average = BigDecimal.valueOf(reviewRepository.averageRating(productId)).setScale(2, RoundingMode.HALF_UP);
        productRepository.updateRating(productId, average, (int) reviewRepository.countByProductId(productId));
    }

    private static Sort sortOf(ReviewSort sort) {
        return switch (sort == null ? ReviewSort.RECENT : sort) {
            case RECENT -> Sort.by("createdAt").descending().and(Sort.by("id").descending());
            case HELPFUL -> Sort.by("helpfulCount").descending().and(Sort.by("createdAt").descending());
            case HIGHEST -> Sort.by("rating").descending().and(Sort.by("createdAt").descending());
            case LOWEST -> Sort.by("rating").ascending().and(Sort.by("createdAt").descending());
        };
    }

    private static String authorName(User author) {
        return author.getFirstName() + " " + author.getLastName().charAt(0) + ".";
    }

    private static ReviewDto toDto(Review review, boolean voted, Long viewerId) {
        return new ReviewDto(review.getId(), review.getRating(), review.getComment(), authorName(review.getUser()),
                review.getCreatedAt(), review.getHelpfulCount(), voted,
                viewerId != null && review.getUser().getId().equals(viewerId), List.copyOf(review.getPhotoUrls()),
                review.getSellerReply(), review.getSellerRepliedAt());
    }

    private static SellerReviewDto toSellerDto(Review review) {
        Product product = review.getProduct();
        String image = product.getImages().isEmpty() ? null : product.getImages().getFirst().getUrl();
        return new SellerReviewDto(review.getId(), product.getId(), product.getName(), image, review.getRating(),
                review.getComment(), authorName(review.getUser()), review.getCreatedAt(), review.getHelpfulCount(),
                List.copyOf(review.getPhotoUrls()), review.getSellerReply(), review.getSellerRepliedAt());
    }
}
