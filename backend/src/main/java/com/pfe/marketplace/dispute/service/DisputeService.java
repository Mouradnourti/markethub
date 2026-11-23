package com.pfe.marketplace.dispute.service;

import com.pfe.marketplace.common.dto.PageResponse;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.dispute.dto.DisputeDtos.DisputeDto;
import com.pfe.marketplace.dispute.dto.DisputeDtos.OpenDisputeRequest;
import com.pfe.marketplace.dispute.dto.DisputeDtos.ResolveDisputeRequest;
import com.pfe.marketplace.dispute.entity.Dispute;
import com.pfe.marketplace.dispute.entity.DisputeStatus;
import com.pfe.marketplace.dispute.entity.DisputeType;
import com.pfe.marketplace.dispute.repository.DisputeRepository;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.order.service.OrderLifecycleService;
import com.pfe.marketplace.user.repository.UserRepository;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Return requests and disputes: opened by customers, decided by admins. */
@Service
@RequiredArgsConstructor
public class DisputeService {

    private static final Set<OrderStatus> DISPUTABLE = EnumSet.of(
            OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final DisputeRepository disputeRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final UserRepository userRepository;
    private final OrderLifecycleService lifecycle;
    private final Clock clock;

    @Transactional
    public DisputeDto open(Long customerId, Long shopOrderId, OpenDisputeRequest request) {
        ShopOrder shopOrder = shopOrderRepository.findForCustomer(shopOrderId, customerId)
                .orElseThrow(() -> new NotFoundException("Order", shopOrderId));
        DisputeType type = request.type() == null ? DisputeType.RETURN : request.type();
        if (type == DisputeType.RETURN && shopOrder.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException("A return can only be requested for a delivered parcel");
        }
        if (type == DisputeType.DISPUTE && !DISPUTABLE.contains(shopOrder.getStatus())) {
            throw new BusinessException("This parcel cannot be disputed in its current state");
        }
        if (disputeRepository.existsByShopOrderIdAndStatus(shopOrderId, DisputeStatus.OPEN)) {
            throw new ConflictException("A request is already open for this parcel");
        }
        Dispute dispute = new Dispute();
        dispute.setShopOrder(shopOrder);
        dispute.setCustomer(userRepository.getReferenceById(customerId));
        dispute.setType(type);
        dispute.setReason(request.reason().trim());
        return toDto(disputeRepository.save(dispute));
    }

    @Transactional(readOnly = true)
    public PageResponse<DisputeDto> list(DisputeStatus status, int page, int size) {
        var pageable = PageRequest.of(page, Math.clamp(size, 1, 100), Sort.by("createdAt").descending());
        return PageResponse.of(disputeRepository.search(status, pageable), DisputeService::toDto);
    }

    /** Approving a RETURN on a delivered parcel moves it to RETURNED (stock back, refund status updated). */
    @Transactional
    public DisputeDto resolve(Long adminId, Long disputeId, ResolveDisputeRequest request) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute", disputeId));
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new ConflictException("This request was already resolved");
        }
        if (request.decision() == DisputeStatus.OPEN) {
            throw new BusinessException("The decision must be APPROVED or REJECTED");
        }
        dispute.setStatus(request.decision());
        dispute.setAdminNote(request.note());
        dispute.setResolvedAt(clock.instant());

        ShopOrder shopOrder = dispute.getShopOrder();
        if (request.decision() == DisputeStatus.APPROVED && dispute.getType() == DisputeType.RETURN
                && shopOrder.getStatus() == OrderStatus.DELIVERED) {
            lifecycle.transition(shopOrder, OrderStatus.RETURNED, "Return approved by the platform",
                    userRepository.getReferenceById(adminId));
        }
        return toDto(dispute);
    }

    private static DisputeDto toDto(Dispute d) {
        ShopOrder so = d.getShopOrder();
        return new DisputeDto(d.getId(), d.getType(), d.getStatus(), d.getReason(), d.getAdminNote(), so.getId(),
                so.getStatus(), so.getOrder().getReference(), so.getShop().getName(), so.getSubtotal(),
                d.getCustomer().getId(), d.getCustomer().getFullName(), d.getCreatedAt(), d.getResolvedAt());
    }
}
