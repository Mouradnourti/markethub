package com.pfe.marketplace.order.service;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.dispute.repository.DisputeRepository;
import com.pfe.marketplace.order.dto.OrderDtos.OrderDto;
import com.pfe.marketplace.order.entity.Order;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.mapper.OrderMapper;
import com.pfe.marketplace.order.repository.OrderRepository;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer-side order queries and cancellation. */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final DisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final OrderLifecycleService lifecycle;
    private final OrderMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<OrderDto> list(Long customerId, int page, int size) {
        Page<Order> orders = orderRepository.findByCustomerId(customerId,
                PageRequest.of(page, Math.clamp(size, 1, 50), Sort.by("createdAt").descending()));
        Set<Long> openReturns = openReturns(orders.getContent());
        return PageResponse.of(orders, order -> mapper.toDto(order, openReturns));
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long customerId, Long orderId) {
        Order order = find(customerId, orderId);
        return mapper.toDto(order, openReturns(List.of(order)));
    }

    /** Cancels the whole order; only possible while no seller has confirmed any parcel yet. */
    @Transactional
    public OrderDto cancel(Long customerId, Long orderId, String reason) {
        Order order = find(customerId, orderId);
        boolean cancellable = order.getShopOrders().stream().allMatch(so -> so.getStatus().customerCanCancel());
        if (!cancellable) {
            throw new BusinessException("This order can no longer be cancelled because a seller already processed it");
        }
        User customer = userRepository.getReferenceById(customerId);
        String note = reason == null || reason.isBlank() ? "Cancelled by customer" : "Cancelled by customer: " + reason;
        for (ShopOrder shopOrder : order.getShopOrders()) {
            lifecycle.transition(shopOrder, OrderStatus.CANCELLED, note, customer);
        }
        return mapper.toDto(order, Set.of());
    }

    private Order find(Long customerId, Long orderId) {
        return orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private Set<Long> openReturns(List<Order> orders) {
        List<Long> ids = orders.stream().flatMap(o -> o.getShopOrders().stream()).map(ShopOrder::getId).toList();
        return ids.isEmpty() ? Set.of() : disputeRepository.findShopOrdersWithOpenDispute(ids);
    }
}
