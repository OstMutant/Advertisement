package org.ost.platform.core;

/**
 * Thrown when an edit or delete is attempted after the entity's allowed edit window has elapsed.
 * A distinct type from {@link IllegalStateException} so callers (HTTP error mapping, UI dialogs)
 * can distinguish this business-rule rejection from an unrelated unexpected-state bug without
 * relying on the exception's message text.
 */
public class EditWindowClosedException extends RuntimeException {

    public EditWindowClosedException(String message) {
        super(message);
    }
}
