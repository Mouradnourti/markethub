package com.pfe.marketplace.auth.dto;

import com.pfe.marketplace.user.dto.UserDto;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
}
