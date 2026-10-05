package org.ost.platform.feedback.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

/** Create/update input for one comment -- {@code id} present means edit, absent means a new reply. */
@FieldNameConstants
public record FeedbackCommentSaveDto(
        Long id,
        @NonNull Long feedbackId,
        Long parentCommentId,
        @NonNull Long authorId,
        @NotBlank @Size(max = TEXT_RAW_MAX_LENGTH) String commentText,
        Long version
) {
    public static final int TEXT_MAX_LENGTH     = 2000;
    public static final int TEXT_RAW_MAX_LENGTH = 20_000;
    public static final int MAX_DEPTH = 3; // level 1 = direct comment on the feedback entry, level 3 = deepest allowed reply
}
