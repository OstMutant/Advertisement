package org.ost.platform.feedback.model;

/** Visibility state of one feedback entry -- NEW is visible by default, HIDDEN is pending admin moderation review, CONFIRMED_HIDDEN is a moderator's confirmation that it's a genuine violation (left out of the default moderation queue view, still rendered as censored, can still be approved back). */
public enum FeedbackModerationStatus {
    NEW,
    HIDDEN,
    CONFIRMED_HIDDEN;

    /** True for HIDDEN (pending review) and CONFIRMED_HIDDEN (confirmed violation), the two non-public states. */
    public boolean isHidden() {
        return this == HIDDEN || this == CONFIRMED_HIDDEN;
    }
}
