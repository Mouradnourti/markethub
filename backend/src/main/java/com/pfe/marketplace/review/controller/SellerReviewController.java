package com.pfe.marketplace.review.controller;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.review.dto.ReviewDtos.ReplyRequest;
import com.pfe.marketplace.review.dto.ReviewDtos.SellerReviewDto;
import com.pfe.marketplace.review.service.ReviewService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Seller")
@RestController
@RequestMapping("/api/seller/reviews")
@RequiredArgsConstructor
public class SellerReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "Reviews of the seller's products, newest first")
    @GetMapping
    public PageResponse<SellerReviewDto> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(defaultValue = "false") boolean unanswered,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return reviewService.listForSeller(user.id(), unanswered, page, size);
    }

    @Operation(summary = "Answer a review publicly (the customer is notified the first time)")
    @PutMapping("/{id}/reply")
    public SellerReviewDto reply(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                 @Valid @RequestBody ReplyRequest request) {
        return reviewService.reply(user.id(), id, request.reply());
    }

    @DeleteMapping("/{id}/reply")
    public SellerReviewDto deleteReply(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return reviewService.deleteReply(user.id(), id);
    }
}
