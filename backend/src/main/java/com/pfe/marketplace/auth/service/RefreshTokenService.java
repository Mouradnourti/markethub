package com.pfe.marketplace.auth.service;

import com.pfe.marketplace.auth.entity.RefreshToken;
import com.pfe.marketplace.auth.repository.RefreshTokenRepository;
import com.pfe.marketplace.common.exception.UnauthorizedException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues opaque refresh tokens and rotates them: every token can be used exactly once. */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final Duration ttl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties, Clock clock) {
        this.repository = repository;
        this.ttl = properties.jwt().refreshTokenTtl();
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(clock.instant().plus(ttl));
        repository.save(token);
        return rawToken;
    }

    /**
     * Consumes a refresh token and returns its owner. Presenting an already-revoked token is treated as
     * token theft: every session of that user is revoked.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User consume(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (token.isRevoked()) {
            repository.revokeAllForUser(token.getUser().getId());
            throw new UnauthorizedException("Refresh token already used");
        }
        if (!token.isUsableAt(clock.instant())) {
            throw new UnauthorizedException("Refresh token expired");
        }
        token.setRevoked(true);
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(token -> token.setRevoked(true));
    }

    @Transactional
    public void revokeAll(Long userId) {
        repository.revokeAllForUser(userId);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
