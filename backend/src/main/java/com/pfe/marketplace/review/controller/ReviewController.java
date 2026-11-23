package com.pfe.marketplace.review.controller;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewEligibilityDto;
import com.pfe.marketplace.review.dto.ReviewDtos.ReviewRequest;
import com.pfe.marketplace.review.service.ReviewService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reviews")
@RestController
@RequestMapping("/api/products/{productId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    public PageResponse<ReviewDto> list(@PathVariable Long productId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        return reviewService.list(productId, page, size);
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
}
