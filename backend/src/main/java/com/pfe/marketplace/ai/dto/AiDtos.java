package com.pfe.marketplace.ai.dto;

import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSpecDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contracts of the Python AI service (camelCase JSON) and of the AI features exposed by the backend. */
public final class AiDtos {

    private AiDtos() {
    }

    /** Return-risk features; the same definitions are used for training (dataset export) and scoring. */
    public record ReturnRiskFeatures(
            double total,
            int itemCount,
            int shopCount,
            int priorOrders,
            int priorReturns,
            double priorReturnRate,
            long accountAgeDays,
            int hour,
            int isWeekend,
            int firstOrder,
            String city) {
    }

    public record ReturnRiskResult(double score, String level, List<String> reasons, String model) {
    }

    public record RecommendationRequest(Long userId, int limit, List<Long> excludeProductIds) {
    }

    public record RecommendationResult(List<Long> productIds, String strategy) {
    }

    public record SalesPoint(LocalDate date, BigDecimal revenue) {
    }

    public record ForecastRequest(List<SalesPoint> history, int horizonDays) {
    }

    public record ForecastPoint(LocalDate date, BigDecimal revenue, BigDecimal lower, BigDecimal upper) {
    }

    public record ForecastResult(String model, double mae, double baselineMae, List<ForecastPoint> forecast) {
    }

    /** What the seller dashboard receives; {@code available=false} when the AI service is down. */
    public record SalesForecastDto(boolean available, List<SalesPoint> history, List<ForecastPoint> forecast,
                                   String model, String message) {
    }

    /** What the seller gives the description writer. */
    public record CopyDraftRequest(
            @NotBlank @Size(min = 2, max = 200) String name,
            Long categoryId,
            @Size(max = 12) List<@NotBlank @Size(max = 80) String> keywords,
            @Pattern(regexp = "friendly|premium|technical") String tone,
            @Min(0) @Max(1000) Integer variant) {
    }

    public record AiCopyRequest(String name, String category, List<String> keywords, String tone, int variant) {
    }

    /** {@code engine} is "rules" or the language model that wrote the text. */
    public record CopyDraft(String tagline, String description, List<String> highlights, List<ProductSpecDto> specs,
                            List<String> suggestedSpecLabels, String categoryProfile, String engine) {
    }

    public record AiSearchRequest(String query, int limit) {
    }

    public record AiSearchHit(Long productId, double score) {
    }

    public record AiSearchResult(List<AiSearchHit> hits, String model) {
    }

    /** {@code strategy} is "semantic" (AI index) or "keyword" (database search, when the AI service is down). */
    public record SmartSearchDto(List<com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto> products,
                                 String strategy) {
    }

    public record PriceHintRequest(@NotBlank @Size(min = 2, max = 200) String name, Long categoryId,
                                   @Size(max = 5000) String description) {
    }

    public record AiPriceHintRequest(String name, String category, String description) {
    }

    public record Comparable(Long productId, String name, BigDecimal price, double similarity) {
    }

    public record PriceHintDto(BigDecimal low, BigDecimal typical, BigDecimal high, BigDecimal suggested,
                               String confidence, List<Comparable> comparables) {
    }
}
