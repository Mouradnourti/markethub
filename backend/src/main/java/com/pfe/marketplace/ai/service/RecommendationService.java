package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.client.AiClient;
import com.pfe.marketplace.ai.dto.AiDtos.RecommendationRequest;
import com.pfe.marketplace.catalog.dto.CatalogDtos.ProductSummaryDto;
import com.pfe.marketplace.catalog.service.ProductService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Personalised recommendations from the AI service, completed with best sellers (and fully replaced when it is down). */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final AiClient aiClient;
    private final ProductService productService;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> forCustomer(Long customerId, int limit) {
        List<Long> owned = jdbc.queryForList("""
                select distinct oi.product_id from order_items oi
                join shop_orders so on so.id = oi.shop_order_id join orders o on o.id = so.order_id
                where o.customer_id = ?
                union select product_id from cart_items where user_id = ?""", Long.class, customerId, customerId);

        List<Long> aiPicks = aiClient.recommendations(new RecommendationRequest(customerId, limit * 2, owned))
                .map(result -> result.productIds())
                .orElse(List.of());

        Set<Long> excluded = new LinkedHashSet<>(owned);
        List<ProductSummaryDto> picks = productService.findVisibleByIds(aiPicks).stream()
                .filter(p -> p.stock() > 0 && excluded.add(p.id()))
                .limit(limit)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        if (picks.size() < limit) {
            productService.popular(limit * 3).stream()
                    .filter(p -> excluded.add(p.id()))
                    .limit(limit - picks.size())
                    .forEach(picks::add);
        }
        return picks;
    }
}
