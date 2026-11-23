package com.pfe.marketplace.stats.service;

import static com.pfe.marketplace.stats.service.StatsQueries.SOLD_STATUSES;

import com.pfe.marketplace.stats.dto.StatsDtos.AdminStatsDto;
import com.pfe.marketplace.stats.dto.StatsDtos.LabelValue;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final StatsQueries queries;

    @Transactional(readOnly = true)
    public AdminStatsDto global() {
        var none = new MapSqlParameterSource();
        long totalOrders = queries.count("select count(*) from orders", none);
        BigDecimal gmv = queries.decimal(
                "select coalesce(sum(subtotal),0) from shop_orders where status in " + SOLD_STATUSES, none);
        BigDecimal commission = queries.decimal(
                "select coalesce(sum(commission_amount),0) from shop_orders where status = 'DELIVERED'", none);
        long soldOrders = queries.count("""
                select count(distinct order_id) from shop_orders where status in """ + SOLD_STATUSES, none);
        BigDecimal aov = soldOrders == 0 ? BigDecimal.ZERO
                : gmv.divide(BigDecimal.valueOf(soldOrders), 2, RoundingMode.HALF_UP);

        return new AdminStatsDto(
                queries.countBy("select role, count(*) from users group by role order by role", none),
                queries.countBy("select status, count(*) from shops group by status order by status", none),
                totalOrders, gmv, commission, aov,
                queries.count("select count(*) from disputes where status = 'OPEN'", none),
                queries.countBy("select payment_method, count(*) from orders group by payment_method", none),
                queries.countBy("select status, count(*) from shop_orders group by status order by status", none),
                queries.lastTwelveMonths("""
                        select to_char(date_trunc('month', created_at at time zone 'UTC'), 'YYYY-MM') as month,
                               sum(subtotal) as revenue, sum(commission_amount) as commission,
                               count(distinct order_id) as orders
                        from shop_orders
                        where status in %s
                          and created_at >= date_trunc('month', now() at time zone 'UTC') - interval '11 months'
                        group by 1""".formatted(SOLD_STATUSES), none),
                topLabels("""
                        select c.name as label, sum(oi.line_total) as value
                        from order_items oi
                        join shop_orders so on so.id = oi.shop_order_id
                        join products p on p.id = oi.product_id
                        join categories c on c.id = p.category_id
                        where so.status in %s group by c.name order by value desc limit 6""".formatted(SOLD_STATUSES)),
                topLabels("""
                        select s.name as label, sum(so.subtotal) as value
                        from shop_orders so join shops s on s.id = so.shop_id
                        where so.status in %s group by s.name order by value desc limit 6""".formatted(SOLD_STATUSES)));
    }

    private List<LabelValue> topLabels(String sql) {
        return queries.jdbc().query(sql, new MapSqlParameterSource(),
                (rs, i) -> new LabelValue(rs.getString("label"), rs.getBigDecimal("value")));
    }
}
