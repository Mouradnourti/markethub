package com.pfe.marketplace.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.marketplace.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits sign-in, sign-up and token refresh calls per client IP (fixed one-minute window), against credential
 * stuffing and sign-up spam. Kept in memory per instance; a shared store (Redis) would be used behind a
 * load balancer with several instances.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED = Set.of(
            "/api/auth/login", "/api/auth/login/2fa", "/api/auth/register", "/api/auth/refresh");
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long minute, AtomicInteger count) {
    }

    public AuthRateLimitFilter(SecurityProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        long nowMillis = clock.millis();
        long minute = nowMillis / 60_000;
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(w -> w.minute() < minute);
        }
        Window window = windows.compute(request.getRemoteAddr(), (ip, current) ->
                current == null || current.minute() != minute ? new Window(minute, new AtomicInteger()) : current);
        if (window.count().incrementAndGet() > properties.authRequestsPerMinute()) {
            long retryAfter = Math.max(1, (minute + 1) * 60 - nowMillis / 1000);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts from your network. Please wait a minute and try again.");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), problem);
            return;
        }
        chain.doFilter(request, response);
    }
}
