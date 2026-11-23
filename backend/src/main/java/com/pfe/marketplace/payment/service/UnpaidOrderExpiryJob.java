package com.pfe.marketplace.payment.service;

import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.order.service.OrderLifecycleService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Releases the stock of card orders whose payment was never completed. */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnpaidOrderExpiryJob {

    static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);

    private final ShopOrderRepository shopOrderRepository;
    private final OrderLifecycleService lifecycle;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public int expireUnpaidOrders() {
        List<ShopOrder> expired = shopOrderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.AWAITING_PAYMENT, clock.instant().minus(PAYMENT_WINDOW));
        expired.forEach(parcel -> {
            lifecycle.transition(parcel, OrderStatus.CANCELLED, "Payment not completed within 30 minutes", null);
            parcel.getOrder().setPaymentStatus(PaymentStatus.FAILED);
        });
        if (!expired.isEmpty()) {
            log.info("Cancelled {} unpaid parcel(s)", expired.size());
        }
        return expired.size();
    }
}
