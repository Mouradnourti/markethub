package com.pfe.marketplace.security;

import com.pfe.marketplace.config.AppProperties;
import com.pfe.marketplace.user.entity.Role;
import com.pfe.marketplace.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "markethub";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
        this.clock = clock;
    }

    public String generateAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    /** @throws JwtException if the token is invalid, expired or tampered with. */
    public AuthUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class)));
    }
}
