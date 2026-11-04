package org.ost.platform.feedback.dto;

import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.feedback.model.FeedbackEditPolicy;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.time.Instant;

/** The single reply to a feedback entry, written only by the reviewed entity's owner. */
@FieldNameConstants
public record FeedbackCommentDto(
        @NonNull Long id,
        @NonNull Long feedbackId,
        @NonNull Long authorId,
        String commentText,
        @NonNull FeedbackModerationStatus moderationStatus,
        String moderationReason,
        @NonNull Instant createdAt,
        Instant updatedAt,
        @NonNull Long version
) {
    public boolean isEditable() {
        return Instant.now().isBefore(createdAt.plus(FeedbackEditPolicy.MAX_EDIT_AGE));
    }
}
