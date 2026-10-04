package org.ost.platform.feedback.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;

import java.util.List;

/**
 * Owns rating+text feedback entries attached to any owning entity (PROVIDER_PROFILE or
 * ADVERTISEMENT), generic over the owning entity, plus its own denormalized rating aggregate and
 * the comment-tree discussion attached to each entry. No knowledge of provider-profile or
 * advertisement internals. Implementation lives in feedback-spring-boot-starter.
 */
public interface FeedbackPort {

    List<FeedbackDto> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size);

    int count(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Creates a new feedback entry, or updates the author's own entry within its edit window. */
    FeedbackDto save(@NonNull FeedbackSaveDto dto);

    /** Zero-value aggregate ({@code avgRating=0}, {@code reviewCount=0}) when no feedback exists yet. */
    FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Full comment tree for one feedback entry, in depth-first sibling order; {@code viewerId} may be null for an anonymous visitor. */
    List<FeedbackCommentDto> findCommentsByFeedback(@NonNull Long feedbackId, Long viewerId);

    /** Creates a new reply, or updates the author's own comment within its edit window. */
    FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto);

    /** Toggles the user's reaction on a comment -- same type as the existing one un-votes it. */
    void saveReaction(@NonNull FeedbackCommentReactionSaveDto dto);

    /** Hard-deletes a leaf comment, or tombstones ({@code commentText} set to null) one with replies. */
    void deleteComment(@NonNull Long commentId);
}
