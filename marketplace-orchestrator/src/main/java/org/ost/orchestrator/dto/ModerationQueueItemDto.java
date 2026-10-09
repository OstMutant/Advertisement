package org.ost.orchestrator.dto;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.time.Instant;

/** One row in the merged, oldest-first moderation queue -- a presentation-level discriminated
 *  union over hidden feedback entries and hidden replies, not a new persistence table. */
@Value
@Builder
@FieldNameConstants
public class ModerationQueueItemDto {

    ModerationQueueItemType type;
    Long id;
    Long entityId;
    String contentText;
    Integer rating;
    String moderationReason;
    Long authorId;
    String authorName;
    Instant createdAt;
    FeedbackModerationStatus moderationStatus;
}
