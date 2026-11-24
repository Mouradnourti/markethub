package com.pfe.marketplace.ai.controller;

import com.pfe.marketplace.ai.dto.AiDtos.CopyDraft;
import com.pfe.marketplace.ai.dto.AiDtos.CopyDraftRequest;
import com.pfe.marketplace.ai.dto.AiDtos.SalesForecastDto;
import com.pfe.marketplace.ai.service.MlDatasetService;
import com.pfe.marketplace.ai.service.ProductCopyService;
import com.pfe.marketplace.ai.service.RecommendationService;
import com.pfe.marketplace.ai.service.SalesForecastService;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI")
@RestController
@RequiredArgsConstructor
public class AiController {

    private final RecommendationService recommendationService;
    private final SalesForecastService forecastService;
    private final MlDatasetService datasetService;
    private final ProductCopyService copyService;

    @Operation(summary = "Personalised product recommendations for the signed-in customer")
    @GetMapping("/api/recommendations")
    public List<ProductSummaryDto> recommendations(@AuthenticationPrincipal AuthUser user,
                                                   @RequestParam(defaultValue = "8") int limit) {
        return recommendationService.forCustomer(user.id(), Math.clamp(limit, 1, 24));
    }

    @Operation(summary = "Draft a tagline, description, key benefits and specs for a product (AI writer)")
    @PostMapping("/api/seller/products/describe")
    public CopyDraft describe(@Valid @RequestBody CopyDraftRequest request) {
        return copyService.draft(request);
    }

    @Operation(summary = "AI sales forecast of the seller's shop")
    @GetMapping("/api/seller/forecast")
    public SalesForecastDto forecast(@AuthenticationPrincipal AuthUser user,
                                     @RequestParam(defaultValue = "30") int days) {
        return forecastService.forSeller(user.id(), Math.clamp(days, 7, 90));
    }

    @Operation(summary = "Training dataset for the AI service (internal, X-Internal-Key)")
    @GetMapping("/api/internal/ml/dataset")
    public Map<String, Object> dataset() {
        return datasetService.export();
    }
}
