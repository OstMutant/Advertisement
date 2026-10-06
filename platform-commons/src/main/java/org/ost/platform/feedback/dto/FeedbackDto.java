package org.ost.platform.feedback.dto;

import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.time.Instant;

/** One rating+text feedback entry attached to an owning entity (PROVIDER_PROFILE or ADVERTISEMENT). */
@FieldNameConstants
public record FeedbackDto(
        @NonNull Long id,
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @NonNull Long authorId,
        int rating,
        @NonNull String feedbackText,
        @NonNull FeedbackModerationStatus moderationStatus,
        @NonNull Instant createdAt,
        Instant updatedAt,
        @NonNull Long version
) {
}
