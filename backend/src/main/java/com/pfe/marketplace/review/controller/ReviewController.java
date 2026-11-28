package com.pfe.marketplace.review.controller;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewEligibilityDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewRequest;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewSort;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewSummaryDto;
import com.pfe.marketplace.review.service.ReviewService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Reviews")
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Reviews of a product, sortable and filterable by star rating")
    @GetMapping
    public PageResponse<ReviewDto> list(@PathVariable Long productId, @AuthenticationPrincipal AuthUser user,
                                        @RequestParam(defaultValue = "RECENT") ReviewSort sort,
                                        @RequestParam(required = false) Integer rating,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        Integer stars = rating == null ? null : Math.clamp(rating, 1, 5);
        return reviewService.list(productId, user == null ? null : user.id(), sort, stars, page, size);
    }

    @Operation(summary = "Average, star breakdown and number of reviews with photos")
    @GetMapping("/summary")
    public ReviewSummaryDto summary(@PathVariable Long productId) {
        return reviewService.summary(productId);
    }

    @Operation(summary = "Whether the current user may review this product")
    @GetMapping("/eligibility")
    public ReviewEligibilityDto eligibility(@PathVariable Long productId, @AuthenticationPrincipal AuthUser user) {
        return reviewService.eligibility(user == null ? null : user.id(), productId);
    }

    @Operation(summary = "Review a product the customer has received")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ReviewDto create(@PathVariable Long productId, @AuthenticationPrincipal AuthUser user,
                            @Valid @RequestBody ReviewRequest request) {
        return reviewService.create(user.id(), productId, request);
    }

    @Operation(summary = "Mark a review as helpful")
    @PostMapping("/{reviewId}/helpful")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ReviewDto helpful(@PathVariable Long productId, @PathVariable Long reviewId,
                             @AuthenticationPrincipal AuthUser user) {
        return reviewService.voteHelpful(user.id(), productId, reviewId, true);
    }

    @DeleteMapping("/{reviewId}/helpful")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ReviewDto notHelpful(@PathVariable Long productId, @PathVariable Long reviewId,
                                @AuthenticationPrincipal AuthUser user) {
        return reviewService.voteHelpful(user.id(), productId, reviewId, false);
    }

    @Operation(summary = "Add a photo to your own review (JPEG, PNG, WebP, GIF, max 5 MB, 3 photos)")
    @PostMapping(value = "/{reviewId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ReviewDto addPhoto(@PathVariable Long productId, @PathVariable Long reviewId,
                              @AuthenticationPrincipal AuthUser user, @RequestPart("file") MultipartFile file) {
        return reviewService.addPhoto(user.id(), productId, reviewId, file);
    }
}
