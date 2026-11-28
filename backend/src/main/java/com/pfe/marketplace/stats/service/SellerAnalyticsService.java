package com.pfe.marketplace.stats.service;

import static com.pfe.marketplace.stats.service.StatsQueries.SOLD_STATUSES;

import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.stats.dto.StatsDtos.AnalyticsDay;
import com.pfe.marketplace.stats.dto.StatsDtos.ProductPerformance;
import com.pfe.marketplace.stats.dto.StatsDtos.SellerAnalyticsDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversion analytics for a seller: product page views (recorded by {@link ProductViewService}) against
 * orders, per day and per product, with returns. A "conversion" is an order containing the product.
 */
@Service
@RequiredArgsConstructor
public class SellerAnalyticsService {

    private final StatsQueries queries;
    private final ShopService shopService;
    private final Clock clock;

    private record DaySales(long orders, BigDecimal revenue) {
    }

    @Transactional(readOnly = true)
    public SellerAnalyticsDto forSeller(Long sellerId, int requestedDays) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        int days = Math.clamp(requestedDays, 7, 365);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = today.minusDays(days - 1L);
        var params = new MapSqlParameterSource("shopId", shop.getId()).addValue("from", from)
                .addValue("fromTs", Timestamp.from(from.atStartOfDay(ZoneOffset.UTC).toInstant()));

        List<ProductPerformance> products = products(params);
        List<AnalyticsDay> daily = daily(params, from, today);
        long views = daily.stream().mapToLong(AnalyticsDay::views).sum();
        long orders = daily.stream().mapToLong(AnalyticsDay::orders).sum();
        BigDecimal revenue = daily.stream().map(AnalyticsDay::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        long units = products.stream().mapToLong(ProductPerformance::units).sum();
        long returns = queries.count("""
                select count(*) from shop_orders
                where shop_id = :shopId and status = 'RETURNED' and created_at >= :fromTs""", params);
        long wishlistSaves = queries.count("""
                select count(*) from wishlist_items w join products p on p.id = w.product_id
                where p.shop_id = :shopId and w.created_at >= :fromTs""", params);

        return new SellerAnalyticsDto(days, views, orders, units, revenue, percent(orders, views),
                orders == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP),
                percent(returns, orders + returns), wishlistSaves, daily, products);
    }

    private List<AnalyticsDay> daily(MapSqlParameterSource params, LocalDate from, LocalDate today) {
        Map<LocalDate, Long> views = new HashMap<>();
        queries.jdbc().query("""
                select pv.day, sum(pv.views) as views from product_views pv join products p on p.id = pv.product_id
                where p.shop_id = :shopId and pv.day >= :from group by pv.day""", params,
                rs -> {
                    views.put(rs.getObject("day", LocalDate.class), rs.getLong("views"));
                });
        Map<LocalDate, DaySales> sales = new HashMap<>();
        queries.jdbc().query("""
                select (created_at at time zone 'UTC')::date as day, count(*) as orders, sum(subtotal) as revenue
                from shop_orders where shop_id = :shopId and status in %s and created_at >= :fromTs
                group by 1""".formatted(SOLD_STATUSES), params,
                rs -> {
                    sales.put(rs.getObject("day", LocalDate.class),
                            new DaySales(rs.getLong("orders"), rs.getBigDecimal("revenue")));
                });
        return from.datesUntil(today.plusDays(1)).map(day -> {
            DaySales sale = sales.getOrDefault(day, new DaySales(0, BigDecimal.ZERO));
            return new AnalyticsDay(day, views.getOrDefault(day, 0L), sale.orders(), sale.revenue());
        }).toList();
    }

    private List<ProductPerformance> products(MapSqlParameterSource params) {
        return queries.jdbc().query("""
                with v as (select product_id, sum(views) as views from product_views where day >= :from group by product_id),
                s as (select oi.product_id, count(distinct so.id) as orders, sum(oi.quantity) as units,
                             sum(oi.line_total) as revenue
                      from order_items oi join shop_orders so on so.id = oi.shop_order_id
                      where so.shop_id = :shopId and so.status in %s and so.created_at >= :fromTs
                      group by oi.product_id),
                r as (select oi.product_id, count(distinct so.id) as returns
                      from order_items oi join shop_orders so on so.id = oi.shop_order_id
                      where so.shop_id = :shopId and so.status = 'RETURNED' and so.created_at >= :fromTs
                      group by oi.product_id)
                select p.id, p.name, p.stock, p.avg_rating,
                       (select url from product_images i where i.product_id = p.id order by i.position, i.id limit 1) as image,
                       coalesce(v.views, 0) as views, coalesce(s.orders, 0) as orders, coalesce(s.units, 0) as units,
                       coalesce(s.revenue, 0) as revenue, coalesce(r.returns, 0) as returns
                from products p
                left join v on v.product_id = p.id left join s on s.product_id = p.id left join r on r.product_id = p.id
                where p.shop_id = :shopId
                order by coalesce(s.revenue, 0) desc, coalesce(v.views, 0) desc, p.id
                limit 100""".formatted(SOLD_STATUSES), params, (rs, i) -> {
            long views = rs.getLong("views");
            long orders = rs.getLong("orders");
            long returns = rs.getLong("returns");
            return new ProductPerformance(rs.getLong("id"), rs.getString("name"), rs.getString("image"),
                    rs.getInt("stock"), rs.getBigDecimal("avg_rating"), views, orders, rs.getLong("units"),
                    rs.getBigDecimal("revenue"), percent(orders, views), returns, percent(returns, orders + returns));
        });
    }

    static BigDecimal percent(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }
}
