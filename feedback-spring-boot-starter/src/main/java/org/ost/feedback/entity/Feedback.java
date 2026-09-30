package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** One rating+text feedback row -- at most one per (authorId, entityType, entityId), no FK, resolved only via FeedbackPort. */
@Value
@Builder
@FieldNameConstants
@Table("feedback")
public class Feedback {

    @Id
    Long id;

    EntityType entityType;
    Long entityId;
    Long authorId;
    int rating;
    String feedbackText;
    FeedbackModerationStatus moderationStatus;

    @CreatedDate
    Instant createdAt;

    // write-only from Java's side -- Spring Data JDBC populates it on save; read back via raw SQL elsewhere, not via this field.
    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
