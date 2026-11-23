package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.ai.client.AiClient;
import com.pfe.marketplace.ai.dto.AiDtos.ForecastRequest;
import com.pfe.marketplace.ai.dto.AiDtos.SalesForecastDto;
import com.pfe.marketplace.ai.dto.AiDtos.SalesPoint;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.stats.service.SellerStatsService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sends a seller's daily revenue history to the AI service and returns the forecast for the dashboard chart. */
@Service
@RequiredArgsConstructor
public class SalesForecastService {

    static final int HISTORY_DAYS = 365;
    static final int CHART_HISTORY_DAYS = 90;

    private final ShopService shopService;
    private final SellerStatsService statsService;
    private final AiClient aiClient;

    @Transactional(readOnly = true)
    public SalesForecastDto forSeller(Long sellerId, int horizonDays) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        List<SalesPoint> history = statsService.dailySales(shop.getId(), HISTORY_DAYS).stream()
                .map(p -> new SalesPoint(p.date(), p.revenue()))
                .toList();
        List<SalesPoint> chartHistory = history.subList(Math.max(0, history.size() - CHART_HISTORY_DAYS), history.size());
        if (history.stream().filter(p -> p.revenue().signum() > 0).count() < 14) {
            return new SalesForecastDto(false, chartHistory, List.of(), null,
                    "Not enough sales yet: the forecast appears after two weeks of sales.");
        }
        return aiClient.forecast(new ForecastRequest(history, horizonDays))
                .map(result -> new SalesForecastDto(true, chartHistory, result.forecast(),
                        String.format(Locale.ROOT, "%s · validation MAE %.2f vs %.2f for a same-weekday baseline",
                                result.model(), result.mae(), result.baselineMae()), null))
                .orElseGet(() -> new SalesForecastDto(false, chartHistory, List.of(), null,
                        "The AI service is unavailable right now. Try again in a moment."));
    }
}
