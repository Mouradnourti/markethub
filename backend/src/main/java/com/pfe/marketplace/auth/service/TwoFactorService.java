package com.pfe.marketplace.auth.service;

import com.pfe.marketplace.auth.dto.TwoFactorDtos.TwoFactorSetupDto;
import com.pfe.marketplace.auth.dto.TwoFactorDtos.TwoFactorStatusDto;
import com.pfe.marketplace.common.exception.BusinessException;
import com.pfe.marketplace.common.exception.NotFoundException;
import com.pfe.marketplace.user.entity.User;
import com.pfe.marketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Optional two-factor sign-in with an authenticator app: setup, confirmation, verification, removal. */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final String ISSUER = "MarketHub";

    private final UserRepository userRepository;
    private final TotpService totp;
    private final SecretCipher cipher;

    @Transactional(readOnly = true)
    public TwoFactorStatusDto status(Long userId) {
        return new TwoFactorStatusDto(load(userId).isTotpEnabled());
    }

    /** Creates a new secret to scan; it only protects the account once confirmed with {@link #enable}. */
    @Transactional
    public TwoFactorSetupDto setup(Long userId) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw new BusinessException("Two-factor sign-in is already on; turn it off first to change the device");
        }
        String secret = totp.newSecret();
        user.setTotpSecret(cipher.encrypt(secret));
        return new TwoFactorSetupDto(secret, totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    @Transactional
    public TwoFactorStatusDto enable(Long userId, String code) {
        User user = load(userId);
        if (user.getTotpSecret() == null) {
            throw new BusinessException("Start the two-factor setup first");
        }
        if (!verify(user, code)) {
            throw new BusinessException("This code is not valid. Check the time on your phone and try again");
        }
        user.setTotpEnabled(true);
        return new TwoFactorStatusDto(true);
    }

    @Transactional
    public TwoFactorStatusDto disable(Long userId, String code) {
        User user = load(userId);
        if (!user.isTotpEnabled()) {
            return new TwoFactorStatusDto(false);
        }
        if (!verify(user, code)) {
            throw new BusinessException("This code is not valid");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        return new TwoFactorStatusDto(false);
    }

    public boolean isEnabled(User user) {
        return user.isTotpEnabled() && user.getTotpSecret() != null;
    }

    public boolean verify(User user, String code) {
        return user.getTotpSecret() != null && totp.verify(cipher.decrypt(user.getTotpSecret()), code);
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User", userId));
    }
}
