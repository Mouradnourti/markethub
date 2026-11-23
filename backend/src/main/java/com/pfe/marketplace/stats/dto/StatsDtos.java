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
