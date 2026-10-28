package org.ost.feedback.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** 1:1 star rating for one feedback entry, upserted directly via JdbcClient -- never independently contended. */
@Value
@Builder
@FieldNameConstants
@Table("feedback_rating")
public class FeedbackRating {

    @Id
    Long id;

    Long feedbackId;
    int rating;
    Instant createdAt;
    Instant updatedAt;
}
