package com.pfe.marketplace.auth.dto;

import com.pfe.marketplace.user.dto.UserDto;

/**
 * Tokens of a successful sign-in. When the account has two-factor sign-in, the first step returns only
 * {@code twoFactorRequired} and a short-lived {@code challengeToken} to send back with the code.
 */
public record AuthResponse(String accessToken, String refreshToken, Long expiresIn, UserDto user,
                           Boolean twoFactorRequired, String challengeToken) {

    public AuthResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
        this(accessToken, refreshToken, expiresIn, user, null, null);
    }

    public static AuthResponse challenge(String challengeToken) {
        return new AuthResponse(null, null, null, null, true, challengeToken);
    }
}
