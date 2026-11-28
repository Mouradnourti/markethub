package com.pfe.marketplace.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.pfe.marketplace.auth.service.TotpService;
import com.pfe.marketplace.support.ApiFixtures;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** Brute-force lockout and two-factor sign-in with an authenticator app. */
class AccountSecurityIT extends ApiFixtures {

    @Autowired
    TotpService totp;

    @Test
    void fiveWrongPasswordsLockTheAccountEvenForTheRightPassword() throws Exception {
        String email = uniqueEmail("victim");
        register(email, "CUSTOMER", null);

        for (int i = 0; i < 4; i++) {
            send(null, post("/api/auth/login"), Map.of("email", email, "password", "wrong-" + i), 401);
        }
        JsonNode locked = send(null, post("/api/auth/login"), Map.of("email", email, "password", "wrong-5"), 429);
        assertThat(locked.get("detail").asText()).contains("Try again in 15 minutes");

        perform(null, post("/api/auth/login"), Map.of("email", email, "password", "Password123"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        jdbc.update("update users set locked_until = now() - interval '1 second' where email = ?", email);
        assertThat(login(email, "Password123")).isNotBlank();
        assertThat(jdbc.queryForObject("select failed_login_count from users where email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void twoFactorSignInNeedsTheCodeFromTheAuthenticatorApp() throws Exception {
        String email = uniqueEmail("careful");
        String token = register(email, "CUSTOMER", null);
        assertThat(send(token, get("/api/auth/2fa"), null, 200).get("enabled").asBoolean()).isFalse();

        JsonNode setup = send(token, post("/api/auth/2fa/setup"), null, 200);
        String secret = setup.get("secret").asText();
        assertThat(setup.get("otpauthUri").asText()).startsWith("otpauth://totp/MarketHub:").contains("secret=" + secret);
        assertThat(jdbc.queryForObject("select totp_secret from users where email = ?", String.class, email))
                .isNotBlank().doesNotContain(secret);

        send(token, post("/api/auth/2fa/enable"), Map.of("code", wrongCode(secret)), 422);
        send(token, post("/api/auth/2fa/enable"), Map.of("code", totp.currentCode(secret)), 200);
        assertThat(send(token, get("/api/auth/me"), null, 200).get("twoFactorEnabled").asBoolean()).isTrue();

        JsonNode challenge = send(null, post("/api/auth/login"), Map.of("email", email, "password", "Password123"), 200);
        assertThat(challenge.get("twoFactorRequired").asBoolean()).isTrue();
        assertThat(challenge.has("accessToken")).isFalse();
        String challengeToken = challenge.get("challengeToken").asText();

        // The challenge is not an access token.
        perform(challengeToken, get("/api/auth/me"), null).andExpect(status().isUnauthorized());

        send(null, post("/api/auth/login/2fa"), Map.of("challengeToken", challengeToken, "code", wrongCode(secret)), 401);
        send(null, post("/api/auth/login/2fa"), Map.of("challengeToken", "forged", "code", "123456"), 401);
        JsonNode tokens = send(null, post("/api/auth/login/2fa"),
                Map.of("challengeToken", challengeToken, "code", totp.currentCode(secret)), 200);
        String access = tokens.get("accessToken").asText();
        assertThat(send(access, get("/api/auth/me"), null, 200).get("email").asText()).isEqualTo(email);

        send(access, post("/api/auth/2fa/disable"), Map.of("code", totp.currentCode(secret)), 200);
        assertThat(login(email, "Password123")).isNotBlank();
    }

    private static String wrongCode(String secret) {
        return "000000".equals(secret) ? "111111" : "000000";
    }
}
