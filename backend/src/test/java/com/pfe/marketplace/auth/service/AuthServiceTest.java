package com.pfe.marketplace.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.auth.dto.AccountType;
import com.pfe.marketplace.auth.dto.AuthResponse;
import com.pfe.marketplace.auth.dto.LoginRequest;
import com.pfe.marketplace.auth.dto.RegisterRequest;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.common.exception.UnauthorizedException;
import com.pfe.marketplace.security.JwtService;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.shop.repository.ShopRepository;
import com.pfe.marketplace.user.dto.UserDto;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.mapper.UserMapper;
import com.pfe.marketplace.user.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock ShopRepository shopRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock UserMapper userMapper;
    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(jwtService.generateAccessToken(any())).thenReturn("access");
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(any())).thenReturn("refresh");
    }

    @Test
    void registerCustomerNormalizesEmailAndHashesPassword() {
        AuthResponse response = authService.register(request(" Alice@Mail.COM ", AccountType.CUSTOMER, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@mail.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        verify(shopRepository, never()).save(any());
    }

    @Test
    void registerSellerCreatesPendingShopWithUniqueSlug() {
        when(shopRepository.existsBySlug("atlas-crafts")).thenReturn(true);

        authService.register(request("bob@mail.com", AccountType.SELLER, "Atlas Crafts"));

        ArgumentCaptor<Shop> shop = ArgumentCaptor.forClass(Shop.class);
        verify(shopRepository).save(shop.capture());
        assertThat(shop.getValue().getSlug()).isEqualTo("atlas-crafts-2");
        assertThat(shop.getValue().getStatus()).isEqualTo(ShopStatus.PENDING);
    }

    @Test
    void registerSellerWithoutShopNameFails() {
        assertThatThrownBy(() -> authService.register(request("c@mail.com", AccountType.SELLER, " ")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void registerWithExistingEmailFails() {
        when(userRepository.existsByEmail("taken@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request("taken@mail.com", null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void loginWithValidCredentialsIssuesTokens() {
        User user = user(true);
        when(userRepository.findByEmail("alice@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", "hashed")).thenReturn(true);

        AuthResponse response = authService.login(new LoginRequest("alice@mail.com", "Password1"));

        assertThat(response.accessToken()).isEqualTo("access");
    }

    @Test
    void loginWithWrongPasswordFails() {
        when(userRepository.findByEmail("alice@mail.com")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@mail.com", "bad")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void loginWithDisabledAccountFails() {
        when(userRepository.findByEmail("alice@mail.com")).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@mail.com", "Password1")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void refreshRejectsDisabledUser() {
        when(refreshTokenService.consume("r1")).thenReturn(user(false));

        assertThatThrownBy(() -> authService.refresh("r1")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refreshIssuesNewPair() {
        when(refreshTokenService.consume("r1")).thenReturn(user(true));

        assertThat(authService.refresh("r1").refreshToken()).isEqualTo("refresh");
    }

    @Test
    void logoutRevokesToken() {
        authService.logout("r1");
        verify(refreshTokenService).revoke("r1");
    }

    @Test
    void meReturnsProfileOrNotFound() {
        User user = user(true);
        UserDto dto = new UserDto(1L, "alice@mail.com", "Alice", "A", null, Role.CUSTOMER, true, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        assertThat(authService.me(1L)).isEqualTo(dto);
        assertThatThrownBy(() -> authService.me(2L)).isInstanceOf(NotFoundException.class);
    }

    private static RegisterRequest request(String email, AccountType type, String shopName) {
        return new RegisterRequest(email, "Password1", "Alice", "Smith", null, type, shopName, null);
    }

    private static User user(boolean enabled) {
        User user = new User();
        user.setId(1L);
        user.setEmail("alice@mail.com");
        user.setPasswordHash("hashed");
        user.setRole(Role.CUSTOMER);
        user.setEnabled(enabled);
        return user;
    }
}
