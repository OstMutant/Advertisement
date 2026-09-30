package org.ost.platform.feedback.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;

import java.util.List;

/**
 * Owns rating+text feedback entries attached to any owning entity (PROVIDER_PROFILE or
 * ADVERTISEMENT), generic over the owning entity, plus its own denormalized rating aggregate.
 * No knowledge of provider-profile or advertisement internals. Implementation lives in
 * feedback-spring-boot-starter.
 */
public interface FeedbackPort {

    List<FeedbackDto> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size);

    int count(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Creates a new feedback entry, or updates the author's own entry within its edit window. */
    FeedbackDto save(@NonNull FeedbackSaveDto dto);

    /** Zero-value aggregate ({@code avgRating=0}, {@code reviewCount=0}) when no feedback exists yet. */
    FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId);
}
