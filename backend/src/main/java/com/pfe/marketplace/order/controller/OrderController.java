package com.pfe.marketplace.order.controller;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.dispute.dto.DisputeDtos.DisputeDto;
import com.pfe.marketplace.dispute.dto.DisputeDtos.OpenDisputeRequest;
import com.pfe.marketplace.dispute.service.DisputeService;
import com.pfe.marketplace.order.dto.OrderDtos.CancelOrderRequest;
import com.pfe.marketplace.order.dto.OrderDtos.CheckoutRequest;
import com.pfe.marketplace.order.dto.OrderDtos.CheckoutResponse;
import com.pfe.marketplace.order.dto.OrderDtos.OrderDto;
import com.pfe.marketplace.order.service.CheckoutService;
import com.pfe.marketplace.order.service.OrderService;
import com.pfe.marketplace.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders (customer)")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final DisputeService disputeService;

    @Operation(summary = "Place an order from the cart (COD or CARD)")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.checkout(user.id(), request);
    }

    @GetMapping
    public PageResponse<OrderDto> list(@AuthenticationPrincipal AuthUser user,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        return orderService.list(user.id(), page, size);
    }

    @GetMapping("/{id}")
    public OrderDto get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return orderService.get(user.id(), id);
    }

    @Operation(summary = "Cancel an order that no seller has confirmed yet")
    @PostMapping("/{id}/cancel")
    public OrderDto cancel(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                           @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orderService.cancel(user.id(), id, request == null ? null : request.reason());
    }

    @Operation(summary = "Request a return (delivered parcel) or open a dispute on a shop order")
    @PostMapping("/shop-orders/{shopOrderId}/return-request")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeDto requestReturn(@AuthenticationPrincipal AuthUser user, @PathVariable Long shopOrderId,
                                    @Valid @RequestBody OpenDisputeRequest request) {
        return disputeService.open(user.id(), shopOrderId, request);
    }
}
