package org.ost.platform.feedback.dto;

import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.feedback.model.FeedbackReactionType;

/** Toggle input for one user's reaction on one comment -- same type as the existing reaction un-votes it. */
@FieldNameConstants
public record FeedbackCommentReactionSaveDto(
        @NonNull Long feedbackCommentId,
        @NonNull Long userId,
        @NotNull FeedbackReactionType reactionType
) {
}
