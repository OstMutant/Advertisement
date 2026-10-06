package org.ost.platform.feedback.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

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

    /** Hides a feedback entry pending moderation review. */
    void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId);

    /** Hides a comment pending moderation review. */
    void flagComment(@NonNull Long commentId, @NonNull Long actorId);

    /** Restores a flagged feedback entry to visible. */
    void approveFeedback(@NonNull Long feedbackId);

    /** Restores a flagged comment to visible. */
    void approveComment(@NonNull Long commentId);

    /** Permanently removes a feedback entry and its own comment tree. */
    void rejectFeedback(@NonNull Long feedbackId);

    /** Permanently removes a single comment, regardless of whether it has replies. */
    void rejectComment(@NonNull Long commentId);

    /** Hidden feedback entries awaiting moderation review, oldest first. */
    List<FeedbackDto> findHiddenFeedback(@NonNull Pageable pageable);

    /** Hidden comments awaiting moderation review, oldest first. */
    List<FeedbackCommentDto> findHiddenComments(@NonNull Pageable pageable);

    /** Single feedback entry by its own id, for moderation-action audit snapshots. */
    Optional<FeedbackDto> findFeedbackById(@NonNull Long feedbackId);

    /** Single comment by its own id, for moderation-action audit snapshots. */
    Optional<FeedbackCommentDto> findCommentById(@NonNull Long commentId);
}
