package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.config.PromotionTopRatedProperties;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.platform.promotion.model.PromotionReason;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Unit tests for {@link TopRatedPromotionRule}: both thresholds are inclusive and an absent feedback starter qualifies nobody. */
@ExtendWith(MockitoExtension.class)
class TopRatedPromotionRuleTest {

    private static final Long PROFILE_ID = 7L;

    @Mock private ComponentFactory<FeedbackPort> feedbackPortFactory;
    @Mock private FeedbackPort feedbackPort;

    private TopRatedPromotionRule rule;

    @BeforeEach
    void setUp() {
        rule = new TopRatedPromotionRule(feedbackPortFactory, new PromotionTopRatedProperties(4.8, 5));
    }

    private void stubAggregate(double avg, int count) {
        when(feedbackPortFactory.findIfAvailable()).thenReturn(Optional.of(feedbackPort));
        when(feedbackPort.getAggregate(EntityType.PROVIDER_PROFILE, PROFILE_ID))
                .thenReturn(new FeedbackAggregateDto(EntityType.PROVIDER_PROFILE, PROFILE_ID, avg, count));
    }

    @Test
    void describesItself() {
        assertThat(rule.reason()).isEqualTo(PromotionReason.TOP_RATED);
        assertThat(rule.entityType()).isEqualTo(EntityType.PROVIDER_PROFILE);
    }

    @Test
    void qualifies_exactlyOnBothThresholds_isTrue() {
        stubAggregate(4.8, 5);

        assertThat(rule.qualifies(PROFILE_ID)).isTrue();
    }

    @Test
    void qualifies_averageBelowThreshold_isFalse() {
        stubAggregate(4.7, 50);

        assertThat(rule.qualifies(PROFILE_ID)).isFalse();
    }

    @Test
    void qualifies_tooFewReviews_isFalse() {
        stubAggregate(5.0, 4);

        assertThat(rule.qualifies(PROFILE_ID)).isFalse();
    }

    @Test
    void qualifies_feedbackStarterAbsent_isFalse() {
        when(feedbackPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(rule.qualifies(PROFILE_ID)).isFalse();
    }

    @Test
    void qualifyingIds_delegatesWithTheConfiguredThresholds() {
        when(feedbackPortFactory.findIfAvailable()).thenReturn(Optional.of(feedbackPort));
        when(feedbackPort.findEntityIdsWithRatingAtLeast(EntityType.PROVIDER_PROFILE, 4.8, 5)).thenReturn(Set.of(1L, 2L));

        assertThat(rule.qualifyingIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void qualifyingIds_feedbackStarterAbsent_isEmpty() {
        when(feedbackPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(rule.qualifyingIds()).isEmpty();
    }
}
