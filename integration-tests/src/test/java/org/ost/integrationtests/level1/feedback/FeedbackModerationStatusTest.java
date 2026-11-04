package org.ost.integrationtests.level1.feedback;

import org.junit.jupiter.api.Test;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit test (no Spring context, no DB) — {@link FeedbackModerationStatus#isHidden} is a pure predicate over the enum's own constants. */
class FeedbackModerationStatusTest {

    @Test
    void isHidden_new_returnsFalse() {
        assertThat(FeedbackModerationStatus.NEW.isHidden()).isFalse();
    }

    @Test
    void isHidden_hidden_returnsTrue() {
        assertThat(FeedbackModerationStatus.HIDDEN.isHidden()).isTrue();
    }

    @Test
    void isHidden_confirmedHidden_returnsTrue() {
        assertThat(FeedbackModerationStatus.CONFIRMED_HIDDEN.isHidden()).isTrue();
    }
}
