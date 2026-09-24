package org.ost.platform.core;

/**
 * Thrown when a write conflicts with a newer version of the same row — either a genuine
 * optimistic-lock ({@code @Version}) mismatch, or the row having been deleted between read and
 * write. A project-owned type so callers (UI, REST) depend on a stable contract instead of a
 * persistence-framework exception hierarchy.
 */
public class StaleWriteException extends RuntimeException {

    public StaleWriteException(String message) {
        super(message);
    }

    public StaleWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
