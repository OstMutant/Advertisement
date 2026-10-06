package org.ost.marketplace.services.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.FailureRateLimiter;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Rate-limits contact-reveal clicks, keyed by client IP (+ viewer id when authenticated, since NAT/shared proxies put many real users behind one IP). */
@Service
@RequiredArgsConstructor
public class ContactRevealRateLimiter {

    private static final int MAX_REVEALS = 30;

    private final FailureRateLimiter limiter = new FailureRateLimiter(MAX_REVEALS, Duration.ofMinutes(15));
    private final HttpServletRequest request;

    /** Throws {@link org.ost.platform.core.TooManyAttemptsException} once the key's reveal count reaches {@value MAX_REVEALS} within 15 minutes. */
    public void checkAndRecord(Long viewerId) {
        String key = viewerId != null ? request.getRemoteAddr() + "|" + viewerId : request.getRemoteAddr();
        limiter.checkAllowed(key, "Too many contact reveals, try again later");
        limiter.recordFailure(key);
    }
}
