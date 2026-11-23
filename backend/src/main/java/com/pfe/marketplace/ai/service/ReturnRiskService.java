package com.pfe.marketplace.ai.service;

import com.pfe.marketplace.order.entity.Order;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** COD return-risk scoring. Without the AI service no score is stored and the UI shows no badge. */
@Service
public class ReturnRiskService {

    public BigDecimal score(Order order, Long customerId) {
        return null;
    }
}
