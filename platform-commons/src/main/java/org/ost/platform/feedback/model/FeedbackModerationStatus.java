package org.ost.platform.feedback.model;

/** Visibility state of one feedback entry -- NEW is visible by default, HIDDEN is pending admin moderation review, CONFIRMED_HIDDEN is a moderator's permanent confirmation that it's a genuine violation (excluded from the moderation queue, still rendered as censored). */
public enum FeedbackModerationStatus {
    NEW,
    HIDDEN,
    CONFIRMED_HIDDEN;

    /** True for any non-public state -- both the pending-review HIDDEN and the permanently-resolved CONFIRMED_HIDDEN render the same censored placeholder. */
    public boolean isHidden() {
        return this == HIDDEN || this == CONFIRMED_HIDDEN;
    }
}
