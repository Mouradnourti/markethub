package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.dto.AiDtos.ReturnRiskFeatures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Training dataset for the AI service (GET /api/internal/ml/dataset). Column names are snake_case to match the
 * Python feature definitions. Only aggregated, non-personal data leaves the backend: ids, prices, quantities,
 * timestamps and city names.
 */
@Service
@RequiredArgsConstructor
public class MlDatasetService {

    private final JdbcTemplate jdbc;
    private final CustomerHistoryQueries history;

    @Transactional(readOnly = true)
    public Map<String, Object> export() {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("products", jdbc.queryForList("""
                select p.id, p.name, c.name as category, p.description, p.price, p.shop_id
                from products p join categories c on c.id = p.category_id order by p.id"""));
        dataset.put("interactions", jdbc.queryForList("""
                select o.customer_id as user_id, oi.product_id, sum(oi.quantity) as quantity, min(o.created_at) as created_at
                from order_items oi
                join shop_orders so on so.id = oi.shop_order_id
                join orders o on o.id = so.order_id
                where so.status in ('PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'RETURNED')
                group by o.customer_id, oi.product_id, o.id
                order by created_at"""));
        dataset.put("codOrders", codOrders());
        return dataset;
    }

    /** One labelled row per final COD order, with features computed from the customer's earlier orders only. */
    List<Map<String, Object>> codOrders() {
        Map<Long, ReturnRiskFeatureBuilder> builders = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CustomerHistoryQueries.OrderOutcome order : history.allOrdersChronologically()) {
            ReturnRiskFeatureBuilder builder = builders.computeIfAbsent(order.customerId(), id -> new ReturnRiskFeatureBuilder());
            if (order.cod() && order.finalState() && !order.allCancelled()) {
                ReturnRiskFeatures f = builder.features(order.total(), order.itemCount(), order.shopCount(),
                        order.createdAt(), order.accountCreatedAt(), order.city());
                rows.add(row(f, order.returned()));
            }
            builder.record(order);
        }
        return rows;
    }

    private static Map<String, Object> row(ReturnRiskFeatures f, boolean returned) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("total", f.total());
        row.put("item_count", f.itemCount());
        row.put("shop_count", f.shopCount());
        row.put("prior_orders", f.priorOrders());
        row.put("prior_returns", f.priorReturns());
        row.put("prior_return_rate", f.priorReturnRate());
        row.put("account_age_days", f.accountAgeDays());
        row.put("hour", f.hour());
        row.put("is_weekend", f.isWeekend());
        row.put("first_order", f.firstOrder());
        row.put("city", f.city());
        row.put("returned", returned ? 1 : 0);
        return row;
    }
}
