package com.pfe.marketplace.stats.service;

import static com.pfe.marketplace.stats.service.StatsQueries.SOLD_STATUSES;

import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.stats.dto.StatsDtos.DailyPoint;
import com.pfe.marketplace.stats.dto.StatsDtos.LowStockProduct;
import com.pfe.marketplace.stats.dto.StatsDtos.SellerStatsDto;
import com.pfe.marketplace.stats.dto.StatsDtos.TopProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SellerStatsService {

    public static final int LOW_STOCK_THRESHOLD = 5;

    private final StatsQueries queries;
    private final ShopService shopService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SellerStatsDto forSeller(Long sellerId) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        var params = new MapSqlParameterSource("shopId", shop.getId()).addValue("lowStock", LOW_STOCK_THRESHOLD);

        BigDecimal gross = queries.decimal(
                "select coalesce(sum(subtotal),0) from shop_orders where shop_id = :shopId and status = 'DELIVERED'", params);
        BigDecimal net = queries.decimal("""
                select coalesce(sum(subtotal - commission_amount),0) from shop_orders
                where shop_id = :shopId and status = 'DELIVERED'""", params);
        BigDecimal pipeline = queries.decimal("""
                select coalesce(sum(subtotal),0) from shop_orders
                where shop_id = :shopId and status in ('PENDING','CONFIRMED','SHIPPED')""", params);
        Map<String, Long> byStatus = queries.countBy(
                "select status, count(*) from shop_orders where shop_id = :shopId group by status order by status", params);

        long total = byStatus.entrySet().stream().filter(e -> !e.getKey().equals("AWAITING_PAYMENT"))
                .mapToLong(Map.Entry::getValue).sum();
        long toProcess = byStatus.getOrDefault("PENDING", 0L) + byStatus.getOrDefault("CONFIRMED", 0L);
        long returned = byStatus.getOrDefault("RETURNED", 0L);
        long shipped = returned + byStatus.getOrDefault("DELIVERED", 0L);
        BigDecimal returnRate = shipped == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(returned * 100.0 / shipped).setScale(1, RoundingMode.HALF_UP);

        return new SellerStatsDto(gross, net, pipeline, total, toProcess, returned, returnRate,
                queries.count("select count(*) from products where shop_id = :shopId", params),
                queries.count("select count(*) from products where shop_id = :shopId and active and stock < :lowStock", params),
                queries.decimal("""
                        select coalesce(round(avg(avg_rating), 2), 0) from products
                        where shop_id = :shopId and review_count > 0""", params),
                byStatus,
                queries.lastTwelveMonths("""
                        select to_char(date_trunc('month', created_at at time zone 'UTC'), 'YYYY-MM') as month,
                               sum(subtotal) as revenue, sum(commission_amount) as commission, count(*) as orders
                        from shop_orders
                        where shop_id = :shopId and status in %s
                          and created_at >= date_trunc('month', now() at time zone 'UTC') - interval '11 months'
                        group by 1""".formatted(SOLD_STATUSES), params),
                topProducts(params),
                lowStock(params));
    }

    /** Daily revenue for the last {@code days} days, zero-filled; input for the AI sales forecast. */
    @Transactional(readOnly = true)
    public List<DailyPoint> dailySales(Long shopId, int days) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = today.minusDays(days - 1L);
        var params = new MapSqlParameterSource("shopId", shopId).addValue("from", from);
        Map<LocalDate, DailyPoint> byDay = new LinkedHashMap<>();
        queries.jdbc().query("""
                select (so.created_at at time zone 'UTC')::date as day, sum(oi.line_total) as revenue, sum(oi.quantity) as units
                from shop_orders so join order_items oi on oi.shop_order_id = so.id
                where so.shop_id = :shopId and so.status in %s and (so.created_at at time zone 'UTC')::date >= :from
                group by 1""".formatted(SOLD_STATUSES), params, rs -> {
            LocalDate day = rs.getObject("day", LocalDate.class);
            byDay.put(day, new DailyPoint(day, rs.getBigDecimal("revenue"), rs.getLong("units")));
        });
        return from.datesUntil(today.plusDays(1))
                .map(day -> byDay.getOrDefault(day, new DailyPoint(day, BigDecimal.ZERO, 0)))
                .toList();
    }

    private List<TopProduct> topProducts(MapSqlParameterSource params) {
        return queries.jdbc().query("""
                select oi.product_id, oi.product_name, sum(oi.quantity) as units, sum(oi.line_total) as revenue
                from order_items oi join shop_orders so on so.id = oi.shop_order_id
                where so.shop_id = :shopId and so.status in %s
                group by oi.product_id, oi.product_name order by revenue desc limit 5""".formatted(SOLD_STATUSES),
                params, (rs, i) -> new TopProduct(rs.getLong("product_id"), rs.getString("product_name"),
                        rs.getLong("units"), rs.getBigDecimal("revenue")));
    }

    private List<LowStockProduct> lowStock(MapSqlParameterSource params) {
        return queries.jdbc().query("""
                select id, name, stock from products
                where shop_id = :shopId and active and stock < :lowStock order by stock, name limit 10""",
                params, (rs, i) -> new LowStockProduct(rs.getLong("id"), rs.getString("name"), rs.getInt("stock")));
    }
}
