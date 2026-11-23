package com.pfe.marketplace.payment.controller;

import com.pfe.marketplace.order.entity.PaymentStatus;
import com.pfe.marketplace.payment.service.PaymentService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    public record ConfirmPaymentRequest(boolean simulateDecline) {
    }

    public record PaymentStatusDto(PaymentStatus paymentStatus) {
    }

    private final PaymentService paymentService;

    @Operation(summary = "Confirm a card payment after the payment form completed (idempotent)")
    @PostMapping("/{orderId}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    public PaymentStatusDto confirm(@AuthenticationPrincipal AuthUser user, @PathVariable Long orderId,
                                    @RequestBody(required = false) ConfirmPaymentRequest request) {
        boolean decline = request != null && request.simulateDecline();
        return new PaymentStatusDto(paymentService.confirm(user.id(), orderId, decline));
    }
}
