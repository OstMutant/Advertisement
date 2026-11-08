package org.ost.platform.feedback.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Owns rating+text feedback entries attached to any owning entity (PROVIDER_PROFILE),
 * generic over the owning entity, plus its own denormalized rating aggregate and
 * the single reply the reviewed entity's owner may leave on each entry. No knowledge of
 * provider-profile internals. Implementation lives in feedback-spring-boot-starter.
 */
public interface FeedbackPort {

    List<FeedbackDto> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size);

    int count(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Creates a new feedback entry, or updates an existing one within its edit window. */
    FeedbackDto save(@NonNull FeedbackSaveDto dto);

    /** Zero-value aggregate ({@code avgRating=0}, {@code reviewCount=0}) when no feedback exists yet. */
    FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Entities of this type whose visible-review average (as stored, one decimal) and review count reach both thresholds. */
    Set<Long> findEntityIdsWithRatingAtLeast(@NonNull EntityType entityType, double minAverage, int minReviews);

    /** The single reply to one feedback entry, if any. */
    Optional<FeedbackCommentDto> findCommentByFeedback(@NonNull Long feedbackId);

    /** Creates the one reply to a feedback entry, or updates an existing reply within the edit window. */
    FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto);

    /** Hard-deletes the reply, within its edit window. */
    void deleteComment(@NonNull Long commentId);

    /** Hides a feedback entry pending moderation review, recording the reporter's reason. */
    void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId, @NonNull String reason);

    /** Hides a reply pending moderation review, recording the reporter's reason. */
    void flagComment(@NonNull Long commentId, @NonNull Long actorId, @NonNull String reason);

    /** Restores a flagged feedback entry to visible. */
    void approveFeedback(@NonNull Long feedbackId);

    /** Restores a flagged reply to visible. */
    void approveComment(@NonNull Long commentId);

    /** Confirms a flagged feedback entry as a genuine violation: still censored, left out of the moderation queue's default pending view (reachable via an explicit status filter) and still restorable by an approval. */
    void confirmFeedbackViolation(@NonNull Long feedbackId);

    /** Confirms a flagged reply as a genuine violation: still censored, left out of the moderation queue's default pending view (reachable via an explicit status filter) and still restorable by an approval. */
    void confirmCommentViolation(@NonNull Long commentId);

    /** Permanently removes a feedback entry and its own reply, if any. */
    void rejectFeedback(@NonNull Long feedbackId);

    /** Permanently removes a reply. */
    void rejectComment(@NonNull Long commentId);

    /** Hard-deletes every feedback entry of the owning entity with its reply, rating and content, and the entity's aggregate row. */
    void deleteByEntity(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Hidden feedback entries awaiting moderation review, oldest first. */
    List<FeedbackDto> findHiddenFeedback(@NonNull Pageable pageable);

    /** Hidden replies awaiting moderation review, oldest first. */
    List<FeedbackCommentDto> findHiddenComments(@NonNull Pageable pageable);

    /** Feedback entries already confirmed as a genuine violation, oldest first. */
    List<FeedbackDto> findConfirmedViolationFeedback(@NonNull Pageable pageable);

    /** Replies already confirmed as a genuine violation, oldest first. */
    List<FeedbackCommentDto> findConfirmedViolationComments(@NonNull Pageable pageable);

    /** Single feedback entry by its own id, empty when absent. */
    Optional<FeedbackDto> findFeedbackById(@NonNull Long feedbackId);

    /** Single reply by its own id, empty when absent. */
    Optional<FeedbackCommentDto> findCommentById(@NonNull Long commentId);

    /** One page of the merged, SQL-filtered/sorted/paged moderation queue of feedback entries and replies (hidden ones unless the filter names other statuses). */
    List<ModerationQueueItemDto> findModerationQueue(@NonNull ModerationQueueFilterDto filter, @NonNull Sort sort, int page, int size);

    /** True total count matching the same filter as {@link #findModerationQueue}, independent of page/size. */
    int countModerationQueue(@NonNull ModerationQueueFilterDto filter);
}
