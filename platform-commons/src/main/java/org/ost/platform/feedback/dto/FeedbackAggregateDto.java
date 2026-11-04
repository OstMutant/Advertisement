package org.ost.platform.feedback.dto;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;

/** Denormalized rating summary for one owning entity, recomputed on every feedback write. */
public record FeedbackAggregateDto(
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        double avgRating,
        int reviewCount
) {
}
