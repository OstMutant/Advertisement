package org.ost.platform.feedback.model;

/** Thrown when a review or reply is flagged while it is already hidden or its violation is confirmed, so HTTP mapping can tell it apart from an unexpected-state bug. */
public class FeedbackNotFlaggableException extends RuntimeException {

    public FeedbackNotFlaggableException(String message) {
        super(message);
    }
}
