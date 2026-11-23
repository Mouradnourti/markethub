package com.pfe.marketplace.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.cart.dto.CartDtos.AddCartItemRequest;
import com.pfe.marketplace.cart.dto.CartDtos.CartDto;
import com.pfe.marketplace.cart.entity.CartItem;
import com.pfe.marketplace.cart.repository.CartItemRepository;
import com.pfe.marketplace.catalog.entity.Product;
import com.pfe.marketplace.catalog.repository.ProductRepository;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.settings.service.SettingsService;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.support.Fixtures;
import com.pfe.marketplace.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;
    @Mock SettingsService settingsService;
    @InjectMocks CartService service;

    Product product = Fixtures.product(1, Fixtures.shop(1, ShopStatus.APPROVED), "15.00", 4);

    @BeforeEach
    void setUp() {
        when(settingsService.shippingFeeFor(any())).thenReturn(new BigDecimal("4.99"));
        when(productRepository.findWithShopAndCategoryById(1L)).thenReturn(Optional.of(product));
    }

    @Test
    void addingTwiceMergesTheLine() {
        CartItem existing = item(product, 1);
        when(cartItemRepository.findByUserIdAndProductId(7L, 1L)).thenReturn(Optional.of(existing));

        service.addItem(7L, new AddCartItemRequest(1L, 2));

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(saved.capture());
        assertThat(saved.getValue().getQuantity()).isEqualTo(3);
    }

    @Test
    void cannotAddMoreThanStock() {
        when(cartItemRepository.findByUserIdAndProductId(7L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addItem(7L, new AddCartItemRequest(1L, 5)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Only 4");
    }

    @Test
    void cannotAddHiddenProduct() {
        product.getShop().setStatus(ShopStatus.SUSPENDED);

        assertThatThrownBy(() -> service.addItem(7L, new AddCartItemRequest(1L, 1)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void totalsIgnoreUnavailableLines() {
        Product soldOut = Fixtures.product(2, Fixtures.shop(2, ShopStatus.APPROVED), "100.00", 0);
        when(cartItemRepository.findByUserIdWithProduct(7L)).thenReturn(List.of(item(product, 2), item(soldOut, 1)));

        CartDto cart = service.get(7L);

        assertThat(cart.itemCount()).isEqualTo(3);
        assertThat(cart.subtotal()).isEqualByComparingTo("30.00");
        assertThat(cart.total()).isEqualByComparingTo("34.99");
        assertThat(cart.items().get(1).available()).isFalse();
    }

    @Test
    void updatingUnknownLineFails() {
        when(cartItemRepository.findByUserIdAndProductId(7L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateQuantity(7L, 9L, 1)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeAndClearDelegateToRepository() {
        service.removeItem(7L, 1L);
        service.clear(7L);

        verify(cartItemRepository).deleteItem(7L, 1L);
        verify(cartItemRepository).deleteAllForUser(7L);
    }

    private static CartItem item(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        return item;
    }
}
