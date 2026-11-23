package com.pfe.marketplace.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Grants ROLE_INTERNAL to service-to-service calls on /api/internal/** that carry the shared key. */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Key";

    private final byte[] expectedKey;

    public InternalApiKeyFilter(String internalApiKey) {
        this.expectedKey = internalApiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/internal/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && MessageDigest.isEqual(expectedKey, provided.getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
