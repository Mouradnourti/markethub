package com.pfe.marketplace.auth.service;

import com.pfe.marketplace.auth.dto.AccountType;
import com.pfe.marketplace.auth.dto.AuthResponse;
import com.pfe.marketplace.auth.dto.LoginRequest;
import com.pfe.marketplace.auth.dto.RegisterRequest;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.ConflictException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.common.exception.TooManyRequestsException;
import com.pfe.marketplace.common.exception.UnauthorizedException;
import com.pfe.marketplace.common.util.Slugs;
import com.pfe.marketplace.security.JwtService;
import com.pfe.marketplace.shop.entity.Shop;
import com.pfe.marketplace.shop.entity.ShopStatus;
import com.pfe.marketplace.shop.repository.ShopRepository;
import com.pfe.marketplace.user.dto.UserDto;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.mapper.UserMapper;
import com.pfe.marketplace.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final LoginAttemptService loginAttempts;
    private final TwoFactorService twoFactor;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account already exists for this email");
        }
        AccountType type = request.accountTypeOrDefault();
        if (type == AccountType.SELLER && isBlank(request.shopName())) {
            throw new BusinessException("A shop name is required to register as a seller");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPhone(request.phone());
        user.setRole(type == AccountType.SELLER ? Role.SELLER : Role.CUSTOMER);
        userRepository.save(user);

        if (type == AccountType.SELLER) {
            createPendingShop(user, request.shopName().trim(), request.shopDescription());
        }
        return issueTokens(user);
    }

    /**
     * Checks the password. Consecutive failures lock the account for a while; with two-factor sign-in on,
     * a correct password only returns a challenge to complete with {@link #completeTwoFactorLogin}.
     */
    @Transactional(noRollbackFor = {UnauthorizedException.class, TooManyRequestsException.class})
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        loginAttempts.ensureNotLocked(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            failed(user);
            throw new UnauthorizedException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        if (twoFactor.isEnabled(user)) {
            return AuthResponse.challenge(jwtService.generateChallengeToken(user));
        }
        loginAttempts.recordSuccess(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, TooManyRequestsException.class})
    public AuthResponse completeTwoFactorLogin(String challengeToken, String code) {
        Long userId;
        try {
            userId = jwtService.parseChallengeToken(challengeToken);
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("This sign-in attempt expired, please enter your password again");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid sign-in attempt"));
        loginAttempts.ensureNotLocked(user);
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        if (!twoFactor.verify(user, code)) {
            failed(user);
            throw new UnauthorizedException("This code is not valid");
        }
        loginAttempts.recordSuccess(user);
        return issueTokens(user);
    }

    private void failed(User user) {
        if (loginAttempts.recordFailure(user)) {
            loginAttempts.ensureNotLocked(user);
        }
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResponse refresh(String refreshToken) {
        User user = refreshTokenService.consume(refreshToken);
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toDto)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    private void createPendingShop(User owner, String shopName, String description) {
        Shop shop = new Shop();
        shop.setOwner(owner);
        shop.setName(shopName);
        shop.setSlug(Slugs.uniqueSlug(shopName, shopRepository::existsBySlug));
        shop.setDescription(description);
        shop.setStatus(ShopStatus.PENDING);
        shopRepository.save(shop);
    }

    private AuthResponse issueTokens(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                refreshTokenService.issue(user),
                jwtService.accessTokenTtlSeconds(),
                userMapper.toDto(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
