package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** One user's current reaction on one comment -- at most one row per (feedbackCommentId, userId). */
@Value
@Builder
@FieldNameConstants
@Table("feedback_comment_reaction")
public class FeedbackCommentReaction {

    @Id
    Long id;

    Long feedbackCommentId;
    Long userId;
    String reactionType;
    Instant createdAt;
}
