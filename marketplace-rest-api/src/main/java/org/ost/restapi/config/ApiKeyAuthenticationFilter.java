package org.ost.restapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Resolves an {@code Authorization: Bearer <key>} header into a plain {@code Long} user-id
 * principal on the security context, via {@link ApiKeyManagementService}. Never touches an
 * authentication already established by another mechanism (e.g. HTTP Basic on the key-issuance
 * endpoint).
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyManagementService apiKeyManagementService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            resolveFromBearerToken(request);
        }
        filterChain.doFilter(request, response);
    }

    private void resolveFromBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return;
        }
        String rawKey = header.substring(BEARER_PREFIX.length());
        apiKeyManagementService.resolveActorId(rawKey)
                .ifPresent(userId -> SecurityContextHolder.getContext().setAuthentication(
                        new PreAuthenticatedAuthenticationToken(userId, null, List.of())));
    }
}
