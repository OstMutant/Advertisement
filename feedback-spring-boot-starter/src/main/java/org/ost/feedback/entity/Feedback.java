package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A link row attaching one feedback_content+feedback_rating pair to an owning entity -- at most one per (authorId, entityType, entityId), no FK, resolved only via FeedbackPort. Immutable after creation. */
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
    Long contentId;
}
