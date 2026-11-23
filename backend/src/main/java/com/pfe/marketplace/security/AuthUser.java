package com.pfe.marketplace.security;

import com.pfe.marketplace.user.entity.Role;

/** Authenticated principal extracted from the JWT. Inject with {@code @AuthenticationPrincipal}. */
public record AuthUser(Long id, String email, Role role) {
}
