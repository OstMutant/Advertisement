package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Denormalized avg-rating/count row -- one per (entityType, entityId), recomputed transactionally on every feedback write. */
@Value
@Builder
@FieldNameConstants
@Table("feedback_aggregate")
public class FeedbackAggregate {

    @Id
    Long id;

    EntityType entityType;
    Long entityId;
    double avgRating;
    int reviewCount;
    Instant updatedAt;
}
