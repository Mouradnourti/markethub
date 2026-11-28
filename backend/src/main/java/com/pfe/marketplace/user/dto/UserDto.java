package com.pfe.marketplace.user.dto;

import com.pfe.marketplace.user.entity.Role;
import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String firstName,
        String lastName,
        String phone,
        Role role,
        boolean enabled,
        boolean twoFactorEnabled,
        Instant createdAt) {
}
