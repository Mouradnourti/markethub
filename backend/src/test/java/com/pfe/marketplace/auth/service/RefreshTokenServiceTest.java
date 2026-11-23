package com.pfe.marketplace.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfe.marketplace.auth.entity.RefreshToken;
import com.pfe.marketplace.auth.repository.RefreshTokenRepository;
import com.pfe.marketplace.common.exception.UnauthorizedException;
import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.user.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock RefreshTokenRepository repository;
    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties("eur", null, "key",
                new AppProperties.Jwt("x".repeat(32), Duration.ofMinutes(15), Duration.ofDays(7)),
                null, null, null, null, null, null);
        service = new RefreshTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issueStoresOnlyTheHash() {
        String raw = service.issue(new User());

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    @Test
    void consumeRevokesTheToken() {
        RefreshToken token = token(false, NOW.plusSeconds(60));
        when(repository.findByTokenHash(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));

        assertThat(service.consume("raw")).isSameAs(token.getUser());
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void consumeExpiredTokenFails() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(token(false, NOW.minusSeconds(1))));

        assertThatThrownBy(() -> service.consume("raw")).hasMessageContaining("expired");
    }

    @Test
    void reusingRevokedTokenRevokesAllSessions() {
        when(repository.findByTokenHash(RefreshTokenService.hash("raw")))
                .thenReturn(Optional.of(token(true, NOW.plusSeconds(60))));

        assertThatThrownBy(() -> service.consume("raw")).isInstanceOf(UnauthorizedException.class);
        verify(repository).revokeAllForUser(42L);
    }

    @Test
    void unknownTokenFails() {
        assertThatThrownBy(() -> service.consume("unknown")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void revokeAllDelegatesToRepository() {
        service.revokeAll(42L);
        verify(repository).revokeAllForUser(42L);
    }

    private static RefreshToken token(boolean revoked, Instant expiresAt) {
        User user = new User();
        user.setId(42L);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
