package com.pfe.marketplace.ai.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One row per order with the outcome flags used by the return-risk features. A COD order is "returned" when any of
 * its parcels was refused or returned, and "final" when every parcel reached a final state.
 */
@Component
class CustomerHistoryQueries {

    record OrderOutcome(long orderId, long customerId, Instant createdAt, Instant accountCreatedAt, boolean cod,
                        double total, int itemCount, int shopCount, String city, boolean returned, boolean finalState,
                        boolean allCancelled) {
    }

    private static final String SELECT = """
            select o.id, o.customer_id, o.created_at, u.created_at as account_created_at, o.payment_method, o.total,
                   o.shipping_city,
                   (select coalesce(sum(oi.quantity), 0) from order_items oi
                      join shop_orders s2 on s2.id = oi.shop_order_id where s2.order_id = o.id) as item_count,
                   count(so.id) as shop_count,
                   bool_or(so.status = 'RETURNED') as returned,
                   bool_and(so.status in ('DELIVERED', 'CANCELLED', 'RETURNED')) as final_state,
                   bool_and(so.status = 'CANCELLED') as all_cancelled
            from orders o
            join users u on u.id = o.customer_id
            join shop_orders so on so.order_id = o.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    CustomerHistoryQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<OrderOutcome> allOrdersChronologically() {
        return jdbc.query(SELECT + " group by o.id, u.created_at order by o.created_at, o.id",
                new MapSqlParameterSource(), (rs, i) -> map(rs));
    }

    List<OrderOutcome> ordersOf(long customerId) {
        return jdbc.query(SELECT + " where o.customer_id = :customerId group by o.id, u.created_at order by o.created_at",
                new MapSqlParameterSource("customerId", customerId), (rs, i) -> map(rs));
    }

    private static OrderOutcome map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OrderOutcome(rs.getLong("id"), rs.getLong("customer_id"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("account_created_at", OffsetDateTime.class).toInstant(),
                "COD".equals(rs.getString("payment_method")), rs.getDouble("total"), rs.getInt("item_count"),
                rs.getInt("shop_count"), rs.getString("shipping_city"), rs.getBoolean("returned"),
                rs.getBoolean("final_state"), rs.getBoolean("all_cancelled"));
    }
}
