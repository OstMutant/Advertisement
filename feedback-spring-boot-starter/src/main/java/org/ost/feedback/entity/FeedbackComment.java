package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** The single reply to a feedback entry -- identity never changes after creation. */
@Value
@Builder
@FieldNameConstants
@Table("feedback_comment")
public class FeedbackComment {

    @Id
    Long id;

    Long contentId;
    Long authorId;
    Long feedbackId;
}
