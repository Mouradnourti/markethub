package com.pfe.marketplace.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String comment) {
    }

    public record ReviewDto(Long id, int rating, String comment, String authorName, Instant createdAt) {
    }

    public record ReviewEligibilityDto(boolean canReview, String reason) {
    }
}
