package org.ost.platform.core;

/**
 * Thrown when an in-memory rate limiter (login, registration) rejects an attempt for exceeding
 * its own failure threshold. A distinct type from {@link IllegalStateException} so callers (HTTP
 * error mapping, UI dialogs) can distinguish a rate-limit rejection from an unrelated
 * unexpected-state bug without relying on the exception's message text.
 */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
