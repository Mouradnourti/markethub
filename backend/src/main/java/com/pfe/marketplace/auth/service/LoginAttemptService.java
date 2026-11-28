package com.pfe.marketplace.auth.service;

import com.pfe.marketplace.common.exception.TooManyRequestsException;
import com.pfe.marketplace.config.SecurityProperties;
import com.pfe.marketplace.user.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Locks an account for a while after too many consecutive wrong passwords or two-factor codes, which stops
 * online password guessing even from many IP addresses.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private final SecurityProperties properties;
    private final Clock clock;
    private final MeterRegistry metrics;

    public void ensureNotLocked(User user) {
        Instant now = clock.instant();
        if (user.getLockedUntil() != null && now.isBefore(user.getLockedUntil())) {
            long minutes = Math.max(1, (Duration.between(now, user.getLockedUntil()).toSeconds() + 59) / 60);
            throw new TooManyRequestsException("Too many failed sign-in attempts. Try again in %d minute%s."
                    .formatted(minutes, minutes > 1 ? "s" : ""), Duration.between(now, user.getLockedUntil()));
        }
    }

    /** Counts a failure; returns true when this failure locked the account. */
    public boolean recordFailure(User user) {
        metrics.counter("markethub.auth.failures").increment();
        int failures = user.getFailedLoginCount() + 1;
        if (failures >= properties.maxFailedLogins()) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(clock.instant().plus(properties.lockoutDuration()));
            metrics.counter("markethub.auth.lockouts").increment();
            return true;
        }
        user.setFailedLoginCount(failures);
        return false;
    }

    public void recordSuccess(User user) {
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
    }
}
