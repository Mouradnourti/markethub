package com.pfe.marketplace.dispute.dto;

import com.pfe.marketplace.dispute.entity.DisputeStatus;
import com.pfe.marketplace.dispute.entity.DisputeType;
import com.pfe.marketplace.order.entity.OrderStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class DisputeDtos {

    private DisputeDtos() {
    }

    public record OpenDisputeRequest(DisputeType type, @NotBlank @Size(min = 5, max = 500) String reason) {
    }

    public record ResolveDisputeRequest(@NotNull DisputeStatus decision, @Size(max = 500) String note) {
    }

    public record DisputeDto(
            Long id,
            DisputeType type,
            DisputeStatus status,
            String reason,
            String adminNote,
            Long shopOrderId,
            OrderStatus shopOrderStatus,
            String orderReference,
            String shopName,
            BigDecimal amount,
            Long customerId,
            String customerName,
            Instant createdAt,
            Instant resolvedAt) {
    }
}
