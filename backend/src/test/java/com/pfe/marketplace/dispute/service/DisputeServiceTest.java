package com.pfe.marketplace.dispute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.dispute.dto.DisputeDtos.DisputeDto;
import com.pfe.marketplace.dispute.dto.DisputeDtos.OpenDisputeRequest;
import com.pfe.marketplace.dispute.dto.DisputeDtos.ResolveDisputeRequest;
import com.pfe.marketplace.dispute.entity.Dispute;
import com.pfe.marketplace.dispute.entity.DisputeStatus;
import com.pfe.marketplace.dispute.entity.DisputeType;
import com.pfe.marketplace.dispute.repository.DisputeRepository;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.order.service.OrderLifecycleService;
import com.pfe.marketplace.support.Fixtures;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisputeServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-01T08:00:00Z");

    @Mock DisputeRepository disputeRepository;
    @Mock ShopOrderRepository shopOrderRepository;
    @Mock UserRepository userRepository;
    @Mock OrderLifecycleService lifecycle;
    DisputeService service;

    @BeforeEach
    void setUp() {
        service = new DisputeService(disputeRepository, shopOrderRepository, userRepository, lifecycle,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(userRepository.getReferenceById(any())).thenAnswer(inv -> Fixtures.user(inv.<Long>getArgument(0), Role.CUSTOMER));
        when(disputeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void returnRequiresDeliveredParcel() {
        givenParcel(OrderStatus.SHIPPED);

        assertThatThrownBy(() -> service.open(1L, 5L, new OpenDisputeRequest(DisputeType.RETURN, "Broken item")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void onlyOneOpenRequestPerParcel() {
        givenParcel(OrderStatus.DELIVERED);
        when(disputeRepository.existsByShopOrderIdAndStatus(5L, DisputeStatus.OPEN)).thenReturn(true);

        assertThatThrownBy(() -> service.open(1L, 5L, new OpenDisputeRequest(null, "Broken item")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void disputeCanBeOpenedOnShippedParcel() {
        givenParcel(OrderStatus.SHIPPED);

        DisputeDto dto = service.open(1L, 5L, new OpenDisputeRequest(DisputeType.DISPUTE, "  Parcel is late  "));

        assertThat(dto.type()).isEqualTo(DisputeType.DISPUTE);
        assertThat(dto.reason()).isEqualTo("Parcel is late");
        assertThat(dto.status()).isEqualTo(DisputeStatus.OPEN);
    }

    @Test
    void approvingReturnMovesParcelToReturned() {
        Dispute dispute = dispute(DisputeType.RETURN, OrderStatus.DELIVERED);

        DisputeDto dto = service.resolve(2L, 3L, new ResolveDisputeRequest(DisputeStatus.APPROVED, "ok"));

        assertThat(dto.status()).isEqualTo(DisputeStatus.APPROVED);
        assertThat(dto.resolvedAt()).isEqualTo(NOW);
        verify(lifecycle).transition(eq(dispute.getShopOrder()), eq(OrderStatus.RETURNED), any(), any());
    }

    @Test
    void rejectingLeavesParcelUntouched() {
        dispute(DisputeType.RETURN, OrderStatus.DELIVERED);

        service.resolve(2L, 3L, new ResolveDisputeRequest(DisputeStatus.REJECTED, "used item"));

        verify(lifecycle, never()).transition(any(), any(), any(), any());
    }

    @Test
    void resolvedDisputeCannotBeResolvedAgainNorSetBackToOpen() {
        Dispute dispute = dispute(DisputeType.RETURN, OrderStatus.DELIVERED);
        assertThatThrownBy(() -> service.resolve(2L, 3L, new ResolveDisputeRequest(DisputeStatus.OPEN, null)))
                .isInstanceOf(BusinessException.class);

        dispute.setStatus(DisputeStatus.REJECTED);
        assertThatThrownBy(() -> service.resolve(2L, 3L, new ResolveDisputeRequest(DisputeStatus.APPROVED, null)))
                .isInstanceOf(ConflictException.class);
    }

    private ShopOrder givenParcel(OrderStatus status) {
        ShopOrder parcel = Fixtures.shopOrder(5, status, PaymentMethod.COD);
        when(shopOrderRepository.findForCustomer(5L, 1L)).thenReturn(Optional.of(parcel));
        return parcel;
    }

    private Dispute dispute(DisputeType type, OrderStatus parcelStatus) {
        Dispute dispute = new Dispute();
        dispute.setId(3L);
        dispute.setType(type);
        dispute.setReason("reason");
        dispute.setCustomer(Fixtures.user(1, Role.CUSTOMER));
        dispute.setShopOrder(Fixtures.shopOrder(5, parcelStatus, PaymentMethod.COD));
        when(disputeRepository.findById(3L)).thenReturn(Optional.of(dispute));
        return dispute;
    }
}
