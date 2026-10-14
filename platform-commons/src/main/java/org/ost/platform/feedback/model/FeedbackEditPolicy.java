package org.ost.platform.feedback.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;

/** Shared edit-window policy for feedback entries and their single reply. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FeedbackEditPolicy {

    public static final Duration MAX_EDIT_AGE = Duration.ofHours(48);
}
