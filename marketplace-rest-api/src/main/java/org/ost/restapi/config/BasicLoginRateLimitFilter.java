package org.ost.restapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.ost.platform.core.FailureRateLimiter;
import org.ost.platform.core.TooManyAttemptsException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/** Blocks the key-issuance endpoint after {@value MAX_FAILURES} failed HTTP Basic logins within {@value WINDOW_MINUTES} minutes for the same client address and email. */
public class BasicLoginRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_FAILURES = 5;
    private static final int WINDOW_MINUTES = 15;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final String BASIC_PREFIX = "Basic ";
    private static final String BLOCKED_MESSAGE = "Too many failed login attempts, try again later";

    private final FailureRateLimiter limiter = new FailureRateLimiter(MAX_FAILURES, Duration.ofMinutes(WINDOW_MINUTES));

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Optional<String> email = basicEmail(request);
        if (email.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = request.getRemoteAddr() + "|" + email.get();
        try {
            limiter.checkAllowed(key, BLOCKED_MESSAGE);
        } catch (TooManyAttemptsException ex) {
            reject(response, ex.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
        int status = response.getStatus();
        if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            limiter.recordFailure(key);
        } else if (status < HttpServletResponse.SC_MULTIPLE_CHOICES) {
            limiter.clear(key);
        }
    }

    private static Optional<String> basicEmail(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
            return Optional.empty();
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon <= 0) {
                return Optional.empty();
            }
            String email = decoded.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            return Optional.of(email.length() > MAX_EMAIL_LENGTH ? email.substring(0, MAX_EMAIL_LENGTH) : email);
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    private static void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(Duration.ofMinutes(WINDOW_MINUTES).toSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
