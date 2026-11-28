package com.pfe.marketplace.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class StatsDtos {

    private StatsDtos() {
    }

    public record MonthlyPoint(String month, BigDecimal revenue, BigDecimal commission, long orders) {
    }

    public record DailyPoint(LocalDate date, BigDecimal revenue, long units) {
    }

    public record LabelValue(String label, BigDecimal value) {
    }

    public record TopProduct(Long productId, String name, long units, BigDecimal revenue) {
    }

    public record LowStockProduct(Long productId, String name, int stock) {
    }

    public record SellerStatsDto(
            BigDecimal grossRevenue,
            BigDecimal netRevenue,
            BigDecimal pipelineRevenue,
            long totalOrders,
            long ordersToProcess,
            long returnedOrders,
            BigDecimal returnRate,
            long productCount,
            long lowStockCount,
            BigDecimal averageRating,
            Map<String, Long> ordersByStatus,
            List<MonthlyPoint> monthly,
            List<TopProduct> topProducts,
            List<LowStockProduct> lowStock) {
    }

    public record AnalyticsDay(LocalDate date, long views, long orders, BigDecimal revenue) {
    }

    /** One product's funnel over the period: page views, orders, units, revenue and returns. */
    public record ProductPerformance(
            Long productId,
            String name,
            String imageUrl,
            int stock,
            BigDecimal rating,
            long views,
            long orders,
            long units,
            BigDecimal revenue,
            BigDecimal conversionRate,
            long returns,
            BigDecimal returnRate) {
    }

    public record SellerAnalyticsDto(
            int days,
            long views,
            long orders,
            long units,
            BigDecimal revenue,
            BigDecimal conversionRate,
            BigDecimal averageOrderValue,
            BigDecimal returnRate,
            long wishlistSaves,
            List<AnalyticsDay> daily,
            List<ProductPerformance> products) {
    }

    public record AdminStatsDto(
            Map<String, Long> usersByRole,
            Map<String, Long> shopsByStatus,
            long totalOrders,
            BigDecimal grossMerchandiseValue,
            BigDecimal commissionEarned,
            BigDecimal averageOrderValue,
            long openDisputes,
            Map<String, Long> paymentMethods,
            Map<String, Long> ordersByStatus,
            List<MonthlyPoint> monthly,
            List<LabelValue> topCategories,
            List<LabelValue> topShops) {
    }
}
