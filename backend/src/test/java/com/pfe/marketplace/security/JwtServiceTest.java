package com.pfe.marketplace.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void generatedTokenRoundTrips() {
        JwtService service = service("a".repeat(40), Clock.fixed(NOW, ZoneOffset.UTC));

        AuthUser parsed = service.parse(service.generateAccessToken(user()));

        assertThat(parsed).isEqualTo(new AuthUser(7L, "seller@mail.com", Role.SELLER));
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service("a".repeat(40), Clock.fixed(NOW, ZoneOffset.UTC)).generateAccessToken(user());
        JwtService later = service("a".repeat(40), Clock.fixed(NOW.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service("a".repeat(40), Clock.systemUTC()).generateAccessToken(user());

        assertThatThrownBy(() -> service("b".repeat(40), Clock.systemUTC()).parse(token))
                .isInstanceOf(JwtException.class);
    }

    private static JwtService service(String secret, Clock clock) {
        AppProperties properties = new AppProperties("eur", null, "key",
                new AppProperties.Jwt(secret, Duration.ofMinutes(15), Duration.ofDays(7)),
                null, null, null, null, null, null);
        return new JwtService(properties, clock);
    }

    private static User user() {
        User user = new User();
        user.setId(7L);
        user.setEmail("seller@mail.com");
        user.setRole(Role.SELLER);
        return user;
    }
}
