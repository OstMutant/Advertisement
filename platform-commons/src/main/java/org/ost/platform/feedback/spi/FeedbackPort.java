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

/**
 * Owns rating+text feedback entries attached to any owning entity (PROVIDER_PROFILE or
 * ADVERTISEMENT), generic over the owning entity, plus its own denormalized rating aggregate and
 * the single reply the reviewed entity's owner may leave on each entry. No knowledge of
 * provider-profile or advertisement internals. Implementation lives in feedback-spring-boot-starter.
 */
public interface FeedbackPort {

    List<FeedbackDto> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size);

    int count(@NonNull EntityType entityType, @NonNull Long entityId);

    /** Creates a new feedback entry, or updates the author's own entry within its edit window. */
    FeedbackDto save(@NonNull FeedbackSaveDto dto);

    /** Zero-value aggregate ({@code avgRating=0}, {@code reviewCount=0}) when no feedback exists yet. */
    FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId);

    /** The single reply to one feedback entry, if any. */
    Optional<FeedbackCommentDto> findCommentByFeedback(@NonNull Long feedbackId);

    /** Creates the one reply to a feedback entry, or updates its author's own reply within the edit window. */
    FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto);

    /** Hard-deletes the reply, within its author's edit window. */
    void deleteComment(@NonNull Long commentId);

    /** Hides a feedback entry pending moderation review, recording the reporter's reason. */
    void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId, @NonNull String reason);

    /** Hides a reply pending moderation review, recording the reporter's reason. */
    void flagComment(@NonNull Long commentId, @NonNull Long actorId, @NonNull String reason);

    /** Restores a flagged feedback entry to visible. */
    void approveFeedback(@NonNull Long feedbackId);

    /** Restores a flagged reply to visible. */
    void approveComment(@NonNull Long commentId);

    /** Permanently confirms a flagged feedback entry as a genuine violation -- stays censored forever, excluded from the moderation queue's default pending view (still reachable via an explicit status filter). */
    void confirmFeedbackViolation(@NonNull Long feedbackId);

    /** Permanently confirms a flagged reply as a genuine violation -- stays censored forever, excluded from the moderation queue's default pending view (still reachable via an explicit status filter). */
    void confirmCommentViolation(@NonNull Long commentId);

    /** Permanently removes a feedback entry and its own reply, if any. */
    void rejectFeedback(@NonNull Long feedbackId);

    /** Permanently removes a reply. */
    void rejectComment(@NonNull Long commentId);

    /** Hidden feedback entries awaiting moderation review, oldest first. */
    List<FeedbackDto> findHiddenFeedback(@NonNull Pageable pageable);

    /** Hidden replies awaiting moderation review, oldest first. */
    List<FeedbackCommentDto> findHiddenComments(@NonNull Pageable pageable);

    /** Feedback entries already confirmed as a genuine violation, oldest first. */
    List<FeedbackDto> findConfirmedViolationFeedback(@NonNull Pageable pageable);

    /** Replies already confirmed as a genuine violation, oldest first. */
    List<FeedbackCommentDto> findConfirmedViolationComments(@NonNull Pageable pageable);

    /** Single feedback entry by its own id, for moderation-action audit snapshots. */
    Optional<FeedbackDto> findFeedbackById(@NonNull Long feedbackId);

    /** Single reply by its own id, for moderation-action audit snapshots. */
    Optional<FeedbackCommentDto> findCommentById(@NonNull Long commentId);

    /** One page of the merged, SQL-filtered/sorted/paged moderation queue (hidden + confirmed-violation feedback and replies). */
    List<ModerationQueueItemDto> findModerationQueue(@NonNull ModerationQueueFilterDto filter, @NonNull Sort sort, int page, int size);

    /** True total count matching the same filter as {@link #findModerationQueue}, independent of page/size. */
    int countModerationQueue(@NonNull ModerationQueueFilterDto filter);
}
