package com.pfe.marketplace.auth.controller;

import com.pfe.marketplace.auth.dto.AuthResponse;
import com.pfe.marketplace.auth.dto.LoginRequest;
import com.pfe.marketplace.auth.dto.RefreshRequest;
import com.pfe.marketplace.auth.dto.RegisterRequest;
import com.pfe.marketplace.auth.dto.TwoFactorDtos.TwoFactorCodeRequest;
import com.pfe.marketplace.auth.dto.TwoFactorDtos.TwoFactorLoginRequest;
import com.pfe.marketplace.auth.dto.TwoFactorDtos.TwoFactorSetupDto;
import com.pfe.marketplace.auth.dto.TwoFactorDtos.TwoFactorStatusDto;
import com.pfe.marketplace.auth.service.AuthService;
import com.pfe.marketplace.auth.service.TwoFactorService;
import com.pfe.marketplace.security.AuthUser;
import com.pfe.marketplace.user.dto.UserDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TwoFactorService twoFactorService;

    @Operation(summary = "Create a customer or seller account (sellers start with a PENDING shop)")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Exchange credentials for an access token and a refresh token")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Second sign-in step for accounts with two-factor sign-in")
    @PostMapping("/login/2fa")
    public AuthResponse loginTwoFactor(@Valid @RequestBody TwoFactorLoginRequest request) {
        return authService.completeTwoFactorLogin(request.challengeToken(), request.code());
    }

    @GetMapping("/2fa")
    public TwoFactorStatusDto twoFactorStatus(@AuthenticationPrincipal AuthUser user) {
        return twoFactorService.status(user.id());
    }

    @Operation(summary = "Start two-factor setup: returns the secret and otpauth URI to scan")
    @PostMapping("/2fa/setup")
    public TwoFactorSetupDto twoFactorSetup(@AuthenticationPrincipal AuthUser user) {
        return twoFactorService.setup(user.id());
    }

    @Operation(summary = "Turn two-factor sign-in on with a code from the authenticator app")
    @PostMapping("/2fa/enable")
    public TwoFactorStatusDto twoFactorEnable(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody TwoFactorCodeRequest request) {
        return twoFactorService.enable(user.id(), request.code());
    }

    @PostMapping("/2fa/disable")
    public TwoFactorStatusDto twoFactorDisable(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody TwoFactorCodeRequest request) {
        return twoFactorService.disable(user.id(), request.code());
    }

    @Operation(summary = "Rotate a refresh token and get a new token pair")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Revoke a refresh token")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "Current user profile")
    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AuthUser user) {
        return authService.me(user.id());
    }
}
