package com.pfe.marketplace.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "must contain at least one letter and one digit")
        String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @Size(max = 30) String phone,
        AccountType accountType,
        @Size(max = 120) String shopName,
        @Size(max = 2000) String shopDescription) {

    public AccountType accountTypeOrDefault() {
        return accountType == null ? AccountType.CUSTOMER : accountType;
    }
}
