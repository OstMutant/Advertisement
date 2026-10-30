package org.ost.restapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.ost.platform.core.FailureRateLimiter;
import org.ost.platform.core.TooManyAttemptsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/** Answers 429 once one account has sent {@code maxRequests} POST/PUT/PATCH/DELETE requests within {@code window}; anonymous requests and reads pass untouched. */
public class ApiWriteRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final String BLOCKED_MESSAGE = "Too many write requests, try again later";

    private final FailureRateLimiter limiter;

    public ApiWriteRateLimitFilter(@NonNull ApiWriteLimitProperties properties) {
        this.limiter = new FailureRateLimiter(properties.maxRequests(), properties.window());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        Optional<Long> actorId = writingActor(request);
        if (actorId.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String key = actorId.get().toString();
        try {
            limiter.checkAllowed(key, BLOCKED_MESSAGE);
        } catch (TooManyAttemptsException ex) {
            TooManyRequestsResponse.write(response, ex);
            return;
        }
        limiter.recordAttempt(key);
        filterChain.doFilter(request, response);
    }

    private static Optional<Long> writingActor(HttpServletRequest request) {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return Optional.empty();
        }
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(Long.class::isInstance)
                .map(Long.class::cast);
    }
}
