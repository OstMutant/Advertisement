package org.ost.platform.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.NonNull;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory sliding-window failure counter, keyed by an arbitrary caller-supplied string (e.g. an
 * email, a client IP, or a composite of both), rejecting further attempts once a key's failure
 * count reaches a fixed threshold within a fixed time window.
 */
public class FailureRateLimiter {

    private final Cache<String, AtomicInteger> attempts;
    private final int maxAttempts;

    public FailureRateLimiter(int maxAttempts, @NonNull Duration window) {
        this.maxAttempts = maxAttempts;
        this.attempts = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(10_000)
                .build();
    }

    /** Throws {@link TooManyAttemptsException} with {@code message} if key is already at or over the threshold. */
    public void checkAllowed(@NonNull String key, @NonNull String message) {
        if (attempts.get(key, _ -> new AtomicInteger(0)).get() >= maxAttempts) {
            throw new TooManyAttemptsException(message);
        }
    }

    public void recordFailure(@NonNull String key) {
        attempts.get(key, _ -> new AtomicInteger(0)).incrementAndGet();
    }

    /** Resets key's counter — only for a caller with a reset-on-success flow (e.g. login); not every caller needs it. */
    public void clear(@NonNull String key) {
        attempts.invalidate(key);
    }
}
