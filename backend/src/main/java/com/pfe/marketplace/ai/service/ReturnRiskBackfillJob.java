package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scores open COD orders that have no risk score yet (placed while the AI service was down, or imported by the demo
 * seed) so sellers always see the badge on the parcels they still have to ship.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnRiskBackfillJob {

    private static final int BATCH = 200;

    private final OrderRepository orderRepository;
    private final ReturnRiskService returnRiskService;

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    @Transactional
    public int backfill() {
        List<Order> orders = orderRepository.findOpenCodOrdersWithoutRiskScore(PageRequest.of(0, BATCH));
        int scored = 0;
        for (Order order : orders) {
            BigDecimal score = returnRiskService.scoreExisting(order);
            if (score == null) {
                break; // AI service unavailable: try again at the next run
            }
            order.setReturnRiskScore(score);
            scored++;
        }
        if (scored > 0) {
            log.info("Scored the return risk of {} open COD order(s)", scored);
        }
        return scored;
    }
}
