package com.pfe.marketplace.ai.dto;

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
}
