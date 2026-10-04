package org.ost.platform.feedback.dto;

import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One reply in a feedback entry's discussion tree, with per-type reactor ids and the viewer's own reaction. */
@FieldNameConstants
public record FeedbackCommentDto(
        @NonNull Long id,
        @NonNull Long feedbackId,
        Long parentCommentId,
        @NonNull Long authorId,
        String commentText,
        @NonNull FeedbackModerationStatus moderationStatus,
        @NonNull Instant createdAt,
        Instant updatedAt,
        @NonNull Long version,
        @NonNull Map<String, List<Long>> reactorIdsByType,
        String myReaction
) {
}
