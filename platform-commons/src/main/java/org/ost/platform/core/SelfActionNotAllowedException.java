package org.ost.platform.core;

/**
 * Thrown when a user attempts a moderation-adjacent action against their own content (e.g.
 * flagging their own feedback or comment as a violation). A distinct type from
 * {@link IllegalStateException} so callers (HTTP error mapping, UI dialogs) can distinguish this
 * business-rule rejection from an unrelated unexpected-state bug without relying on the
 * exception's message text.
 */
public class SelfActionNotAllowedException extends RuntimeException {

    public SelfActionNotAllowedException(String message) {
        super(message);
    }
}
