package com.pfe.marketplace.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.order.dto.OrderDtos.UpdateShopOrderStatusRequest;
import com.pfe.marketplace.order.entity.OrderStatus;
import com.pfe.marketplace.order.entity.PaymentMethod;
import com.pfe.marketplace.order.entity.ShopOrder;
import com.pfe.marketplace.order.mapper.OrderMapper;
import com.pfe.marketplace.order.repository.ShopOrderRepository;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.shop.service.ShopService;
import com.pfe.marketplace.support.Fixtures;
import com.pfe.marketplace.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerOrderServiceTest {

    @Mock ShopOrderRepository shopOrderRepository;
    @Mock ShopService shopService;
    @Mock UserRepository userRepository;
    @Mock OrderLifecycleService lifecycle;
    @Mock OrderMapper mapper;
    @InjectMocks SellerOrderService service;

    Shop shop = Fixtures.shop(3, ShopStatus.APPROVED);

    @BeforeEach
    void setUp() {
        when(shopService.requireApprovedShop(9L)).thenReturn(shop);
    }

    @Test
    void shippingStoresTrackingNumberAndDelegatesTransition() {
        ShopOrder parcel = Fixtures.shopOrder(5, OrderStatus.CONFIRMED, PaymentMethod.COD);
        when(shopOrderRepository.findByIdAndShopId(5L, 3L)).thenReturn(Optional.of(parcel));

        service.updateStatus(9L, 5L, new UpdateShopOrderStatusRequest(OrderStatus.SHIPPED, " TRK-9 ", null));

        assertThat(parcel.getTrackingNumber()).isEqualTo("TRK-9");
        verify(lifecycle).transition(eq(parcel), eq(OrderStatus.SHIPPED), any(), any());
    }

    @Test
    void illegalTransitionIsRejected() {
        ShopOrder parcel = Fixtures.shopOrder(5, OrderStatus.PENDING, PaymentMethod.COD);
        when(shopOrderRepository.findByIdAndShopId(5L, 3L)).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> service.updateStatus(9L, 5L,
                new UpdateShopOrderStatusRequest(OrderStatus.DELIVERED, null, null)))
                .isInstanceOf(BusinessException.class);
        verify(lifecycle, never()).transition(any(), any(), any(), any());
    }

    @Test
    void parcelOfAnotherShopIsNotFound() {
        when(shopOrderRepository.findByIdAndShopId(5L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(9L, 5L)).isInstanceOf(NotFoundException.class);
    }
}
