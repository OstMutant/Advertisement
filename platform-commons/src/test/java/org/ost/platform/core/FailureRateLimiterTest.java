package org.ost.platform.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureRateLimiterTest {

    @Test
    void checkAllowed_underThreshold_doesNotThrow() {
        FailureRateLimiter limiter = new FailureRateLimiter(3, Duration.ofMinutes(1));

        limiter.recordFailure("actor-1");
        limiter.recordFailure("actor-1");

        assertThatCode(() -> limiter.checkAllowed("actor-1", "too many attempts")).doesNotThrowAnyException();
    }

    @Test
    void checkAllowed_atThreshold_throwsTooManyAttemptsException() {
        FailureRateLimiter limiter = new FailureRateLimiter(2, Duration.ofMinutes(1));

        limiter.recordFailure("actor-2");
        limiter.recordFailure("actor-2");

        assertThatThrownBy(() -> limiter.checkAllowed("actor-2", "too many attempts"))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessage("too many attempts");
    }

    @Test
    void recordFailure_isTrackedPerKey_doesNotAffectOtherKeys() {
        FailureRateLimiter limiter = new FailureRateLimiter(1, Duration.ofMinutes(1));

        limiter.recordFailure("actor-3");

        assertThatThrownBy(() -> limiter.checkAllowed("actor-3", "blocked"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.checkAllowed("actor-4", "blocked")).doesNotThrowAnyException();
    }

    @Test
    void clear_resetsKeyCounterBelowThreshold() {
        FailureRateLimiter limiter = new FailureRateLimiter(1, Duration.ofMinutes(1));
        limiter.recordFailure("actor-5");

        limiter.clear("actor-5");

        assertThatCode(() -> limiter.checkAllowed("actor-5", "blocked")).doesNotThrowAnyException();
    }
}
