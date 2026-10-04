package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Shared text+moderation+edit-tracking atom reused by both a feedback entry and a comment. */
@Value
@Builder
@FieldNameConstants
@Table("feedback_content")
public class FeedbackContent {

    @Id
    Long id;

    String contentText;
    FeedbackModerationStatus moderationStatus;

    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
