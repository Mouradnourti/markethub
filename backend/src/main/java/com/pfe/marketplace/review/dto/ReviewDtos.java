package com.pfe.marketplace.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public enum ReviewSort {
        RECENT, HELPFUL, HIGHEST, LOWEST
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {
    }

    public record ReviewDto(
            Long id,
            int rating,
            String comment,
            String authorName,
            Instant createdAt,
            int helpfulCount,
            boolean votedHelpful,
            boolean mine,
            List<String> photoUrls,
            String sellerReply,
            Instant sellerRepliedAt) {
    }

    public record ReviewEligibilityDto(boolean canReview, String reason) {
    }

    /** Star breakdown: {@code counts[0]} is the number of 1-star reviews, {@code counts[4]} of 5-star ones. */
    public record ReviewSummaryDto(BigDecimal average, long total, List<Long> counts, long withPhotos) {
    }

    public record SellerReviewDto(
            Long id,
            Long productId,
            String productName,
            String productImageUrl,
            int rating,
            String comment,
            String authorName,
            Instant createdAt,
            int helpfulCount,
            List<String> photoUrls,
            String sellerReply,
            Instant sellerRepliedAt) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 1000) String reply) {
    }
}
