package org.ost.platform.feedback.model;

/** Thrown when a moderator decides on a review or reply that is not awaiting that decision, so HTTP mapping can tell it apart from an unexpected-state bug. */
public class FeedbackNotModeratableException extends RuntimeException {

    public FeedbackNotModeratableException(String message) {
        super(message);
    }
}
