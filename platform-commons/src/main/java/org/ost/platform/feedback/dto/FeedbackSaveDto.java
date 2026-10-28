package org.ost.platform.feedback.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;

/** Create/update input for one feedback entry -- one row per {@code (authorId, entityType, entityId)}. */
@FieldNameConstants
public record FeedbackSaveDto(
        Long id,
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @NonNull Long authorId,
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(max = TEXT_RAW_MAX_LENGTH) String feedbackText,
        Long version
) {
    public static final int TEXT_MAX_LENGTH     = 2000;
    public static final int TEXT_RAW_MAX_LENGTH = 20_000;
}
