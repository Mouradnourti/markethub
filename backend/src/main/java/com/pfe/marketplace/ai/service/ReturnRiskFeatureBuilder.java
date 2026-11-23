package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.dto.AiDtos.ReturnRiskFeatures;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** Running per-customer counters that turn an order into return-risk features (same logic for training and scoring). */
final class ReturnRiskFeatureBuilder {

    private int orders;
    private int codFinal;
    private int returns;

    /** Features of a new order given everything this builder has seen before it. */
    ReturnRiskFeatures features(double total, int itemCount, int shopCount, Instant placedAt, Instant accountCreatedAt,
                                String city) {
        ZonedDateTime at = placedAt.atZone(ZoneOffset.UTC);
        boolean weekend = at.getDayOfWeek().getValue() >= 6;
        double rate = codFinal == 0 ? 0.0 : (double) returns / codFinal;
        long accountAge = Math.max(0, Duration.between(accountCreatedAt, placedAt).toDays());
        return new ReturnRiskFeatures(total, Math.max(itemCount, 1), Math.max(shopCount, 1), orders, returns,
                Math.round(rate * 10_000) / 10_000.0, accountAge, at.getHour(), weekend ? 1 : 0, orders == 0 ? 1 : 0,
                city == null ? "unknown" : city);
    }

    void record(CustomerHistoryQueries.OrderOutcome order) {
        if (order.cod() && order.finalState() && !order.allCancelled()) {
            codFinal++;
            if (order.returned()) {
                returns++;
            }
        }
        orders++;
    }
}
