package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.config.PromotionTopRatedProperties;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.platform.promotion.model.PromotionReason;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Promotes a provider profile as top rated when its stored review average and visible review count reach the configured thresholds. */
@Service
@RequiredArgsConstructor
public class TopRatedPromotionRule implements PromotionRule {

    private final ComponentFactory<FeedbackPort> feedbackPortFactory;
    private final PromotionTopRatedProperties    properties;

    @Override
    public PromotionReason reason() {
        return PromotionReason.TOP_RATED;
    }

    @Override
    public EntityType entityType() {
        return EntityType.PROVIDER_PROFILE;
    }

    @Override
    public boolean qualifies(@NonNull Long entityId) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.getAggregate(EntityType.PROVIDER_PROFILE, entityId))
                .filter(aggregate -> aggregate.avgRating() >= properties.minAverage()
                        && aggregate.reviewCount() >= properties.minReviews())
                .isPresent();
    }

    @Override
    public Set<Long> qualifyingIds() {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findEntityIdsWithRatingAtLeast(EntityType.PROVIDER_PROFILE, properties.minAverage(), properties.minReviews()))
                .orElse(Set.of());
    }
}
