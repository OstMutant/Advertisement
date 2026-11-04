package org.ost.platform.core;

import java.time.Duration;
import java.util.Optional;

/**
 * Thrown when an in-memory rate limiter (login, registration) rejects an attempt for exceeding
 * its own failure threshold. A distinct type from {@link IllegalStateException} so callers (HTTP
 * error mapping, UI dialogs) can distinguish a rate-limit rejection from an unrelated
 * unexpected-state bug without relying on the exception's message text.
 */
public class TooManyAttemptsException extends RuntimeException {

    private final transient Duration retryAfter;

    public TooManyAttemptsException(String message) {
        this(message, null);
    }

    /** {@code retryAfter} is the limiter's whole window: an upper bound on the wait, null when unknown. */
    public TooManyAttemptsException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
