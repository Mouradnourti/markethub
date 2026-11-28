package com.pfe.marketplace.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class TwoFactorDtos {

    private TwoFactorDtos() {
    }

    /** The secret is shown once, as text and as an otpauth:// URI for the QR code. */
    public record TwoFactorSetupDto(String secret, String otpauthUri) {
    }

    public record TwoFactorStatusDto(boolean enabled) {
    }

    public record TwoFactorCodeRequest(@NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "must be 6 digits")
                                       String code) {
    }

    public record TwoFactorLoginRequest(@NotBlank String challengeToken,
                                        @NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "must be 6 digits")
                                        String code) {
    }
}
