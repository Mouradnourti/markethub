package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.client.AiClient;
import com.pfe.marketplace.ai.dto.AiDtos.ReturnRiskFeatures;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scores the refusal/return probability of a cash-on-delivery order with the AI service. */
@Service
@RequiredArgsConstructor
public class ReturnRiskService {

    private final AiClient aiClient;
    private final CustomerHistoryQueries history;
    private final UserRepository userRepository;
    private final Clock clock;

    /** Score of a new (not yet saved) order, or null when the AI service is unavailable. */
    @Transactional(readOnly = true)
    public BigDecimal score(Order order, Long customerId) {
        int items = order.getShopOrders().stream().flatMap(so -> so.getItems().stream()).mapToInt(i -> i.getQuantity()).sum();
        Instant accountCreated = userRepository.findById(customerId).map(u -> u.getCreatedAt()).orElse(clock.instant());
        ReturnRiskFeatures features = builderFor(customerId, null).features(order.getTotal().doubleValue(), items,
                order.getShopOrders().size(), clock.instant(), accountCreated, order.getShippingCity());
        return request(features);
    }

    /** Score of an existing order, using only the customer's orders placed before it. */
    @Transactional(readOnly = true)
    public BigDecimal scoreExisting(Order order) {
        int items = order.getShopOrders().stream().map(ShopOrder::getItems).mapToInt(list ->
                list.stream().mapToInt(i -> i.getQuantity()).sum()).sum();
        ReturnRiskFeatures features = builderFor(order.getCustomer().getId(), order.getCreatedAt())
                .features(order.getTotal().doubleValue(), items, order.getShopOrders().size(), order.getCreatedAt(),
                        order.getCustomer().getCreatedAt(), order.getShippingCity());
        return request(features);
    }

    private ReturnRiskFeatureBuilder builderFor(Long customerId, Instant before) {
        ReturnRiskFeatureBuilder builder = new ReturnRiskFeatureBuilder();
        history.ordersOf(customerId).stream()
                .filter(o -> before == null || o.createdAt().isBefore(before))
                .forEach(builder::record);
        return builder;
    }

    private BigDecimal request(ReturnRiskFeatures features) {
        return aiClient.returnRisk(features)
                .map(result -> BigDecimal.valueOf(result.score()).setScale(4, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
