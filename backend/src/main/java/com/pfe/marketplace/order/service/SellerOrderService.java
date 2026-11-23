package com.pfe.marketplace.order.service;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.order.dto.OrderDtos.SellerOrderDto;
import com.pfe.marketplace.order.dto.OrderDtos.UpdateShopOrderStatusRequest;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.mapper.OrderMapper;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Order processing for sellers: confirm, ship, deliver, mark returned (COD refused at the door), cancel. */
@Service
@RequiredArgsConstructor
public class SellerOrderService {

    private final ShopOrderRepository shopOrderRepository;
    private final ShopService shopService;
    private final UserRepository userRepository;
    private final OrderLifecycleService lifecycle;
    private final OrderMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<SellerOrderDto> list(Long sellerId, OrderStatus status, int page, int size) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        var pageable = PageRequest.of(page, Math.clamp(size, 1, 100), Sort.by("createdAt").descending());
        return PageResponse.of(shopOrderRepository.findForShop(shop.getId(), status, pageable), mapper::toSellerDto);
    }

    @Transactional(readOnly = true)
    public SellerOrderDto get(Long sellerId, Long shopOrderId) {
        return mapper.toSellerDto(find(sellerId, shopOrderId));
    }

    @Transactional
    public SellerOrderDto updateStatus(Long sellerId, Long shopOrderId, UpdateShopOrderStatusRequest request) {
        ShopOrder shopOrder = find(sellerId, shopOrderId);
        OrderStatus next = request.status();
        if (!shopOrder.getStatus().sellerCanMoveTo(next)) {
            throw new BusinessException("An order cannot go from %s to %s".formatted(shopOrder.getStatus(), next));
        }
        if (next == OrderStatus.SHIPPED && request.trackingNumber() != null && !request.trackingNumber().isBlank()) {
            shopOrder.setTrackingNumber(request.trackingNumber().trim());
        }
        lifecycle.transition(shopOrder, next, request.note(), userRepository.getReferenceById(sellerId));
        return mapper.toSellerDto(shopOrder);
    }

    private ShopOrder find(Long sellerId, Long shopOrderId) {
        Shop shop = shopService.requireApprovedShop(sellerId);
        return shopOrderRepository.findByIdAndShopId(shopOrderId, shop.getId())
                .orElseThrow(() -> new NotFoundException("Order", shopOrderId));
    }
}
