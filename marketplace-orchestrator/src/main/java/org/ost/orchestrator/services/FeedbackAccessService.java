package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSnapshotDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.dto.FeedbackSnapshotDto;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.ost.platform.feedback.model.ModerationQueueItemType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.FeedbackNotModeratableException;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Wraps {@link FeedbackPort} for marketplace-app, degrading gracefully when feedback-spring-boot-starter is absent. */
@Service
@RequiredArgsConstructor
public class FeedbackAccessService {

    private static final String FEEDBACK_PREFIX = "Feedback ";
    private static final String FEEDBACK_COMMENT_PREFIX = "FeedbackComment ";
    private static final String NOT_FOUND_SUFFIX = " not found";

    private final TransactionTemplate            tx;
    private final ComponentFactory<FeedbackPort> feedbackPortFactory;
    private final ComponentFactory<AuditPort>    auditPortFactory;
    private final UserActorNameService           userActorNameService;
    private final AuthorizationService           authorizationService;

    public List<FeedbackDto> findForEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findByEntity(entityType, entityId, page, size))
                .orElse(List.of());
    }

    public FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.getAggregate(entityType, entityId))
                .orElse(new FeedbackAggregateDto(entityType, entityId, 0, 0));
    }

    public FeedbackDto save(@NonNull FeedbackSaveDto dto) {
        return feedbackPortFactory.get().save(dto);
    }

    public boolean isAvailable() {
        return feedbackPortFactory.findIfAvailable().isPresent();
    }

    public Optional<FeedbackDto> findFeedbackById(@NonNull Long feedbackId) {
        return feedbackPortFactory.findIfAvailable().flatMap(port -> port.findFeedbackById(feedbackId));
    }

    public Optional<FeedbackCommentDto> findCommentByFeedback(@NonNull Long feedbackId) {
        return feedbackPortFactory.findIfAvailable().flatMap(port -> port.findCommentByFeedback(feedbackId));
    }

    public Optional<FeedbackCommentDto> findCommentById(@NonNull Long commentId) {
        return feedbackPortFactory.findIfAvailable().flatMap(port -> port.findCommentById(commentId));
    }

    public FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto) {
        return feedbackPortFactory.get().saveComment(dto);
    }

    public void deleteComment(@NonNull Long commentId) {
        feedbackPortFactory.get().deleteComment(commentId);
    }

    public Map<Long, String> resolveAuthorNames(@NonNull Set<Long> authorIds) {
        return userActorNameService.resolveNames(authorIds);
    }

    public List<FeedbackDto> findHiddenFeedback(@NonNull Long actingUserId, @NonNull Pageable pageable) {
        authorizationService.requireIsPrivileged(actingUserId);
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findHiddenFeedback(pageable))
                .orElse(List.of());
    }

    public List<FeedbackCommentDto> findHiddenComments(@NonNull Long actingUserId, @NonNull Pageable pageable) {
        authorizationService.requireIsPrivileged(actingUserId);
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findHiddenComments(pageable))
                .orElse(List.of());
    }

    public List<FeedbackDto> findConfirmedViolationFeedback(@NonNull Long actingUserId, @NonNull Pageable pageable) {
        authorizationService.requireIsPrivileged(actingUserId);
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findConfirmedViolationFeedback(pageable))
                .orElse(List.of());
    }

    public List<FeedbackCommentDto> findConfirmedViolationComments(@NonNull Long actingUserId, @NonNull Pageable pageable) {
        authorizationService.requireIsPrivileged(actingUserId);
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findConfirmedViolationComments(pageable))
                .orElse(List.of());
    }

    /** One page of the merged moderation queue, filtered/sorted/paged entirely in SQL by the feedback starter -- author names resolved here for just this page's authors (display composition stays in this layer, not the starter). */
    public record ModerationQueuePage(List<ModerationQueueItemDto> items, int totalCount) {}

    public ModerationQueuePage findModerationQueue(@NonNull Long actingUserId, @NonNull ModerationQueueFilterDto filter, @NonNull Sort sort, int page, int size) {
        authorizationService.requireIsPrivileged(actingUserId);
        List<ModerationQueueItemDto> items = feedbackPortFactory.findIfAvailable()
                .map(port -> port.findModerationQueue(filter, sort, page, size))
                .orElse(List.of());
        Set<Long> authorIds = items.stream().map(ModerationQueueItemDto::getAuthorId).collect(Collectors.toSet());
        Map<Long, String> names = authorIds.isEmpty() ? Map.of() : resolveAuthorNames(authorIds);
        List<ModerationQueueItemDto> enriched = items.stream()
                .map(item -> item.toBuilder().authorName(names.getOrDefault(item.getAuthorId(), String.valueOf(item.getAuthorId()))).build())
                .toList();
        int total = feedbackPortFactory.findIfAvailable().map(port -> port.countModerationQueue(filter)).orElse(0);
        return new ModerationQueuePage(enriched, total);
    }

    public void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId, @NonNull String reason) {
        tx.executeWithoutResult(status -> {
            feedbackPortFactory.get().flagFeedback(feedbackId, actorId, reason);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, reason), actorId));
        });
    }

    public void flagComment(@NonNull Long commentId, @NonNull Long actorId, @NonNull String reason) {
        tx.executeWithoutResult(status -> {
            feedbackPortFactory.get().flagComment(commentId, actorId, reason);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, reason), actorId));
        });
    }

    public void approveFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        requireAwaitingDecision(feedbackStatus(feedbackId), FEEDBACK_PREFIX + feedbackId, true);
        tx.executeWithoutResult(status -> {
            feedbackPortFactory.get().approveFeedback(feedbackId);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.NEW, null), actorId));
        });
    }

    public void approveComment(@NonNull Long commentId, @NonNull Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        requireAwaitingDecision(commentStatus(commentId), "Comment " + commentId, true);
        tx.executeWithoutResult(status -> {
            feedbackPortFactory.get().approveComment(commentId);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.NEW, null), actorId));
        });
    }

    public void confirmFeedbackViolation(@NonNull Long feedbackId, @NonNull Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        requireAwaitingDecision(feedbackStatus(feedbackId), FEEDBACK_PREFIX + feedbackId, false);
        tx.executeWithoutResult(status -> {
            feedbackPortFactory.get().confirmFeedbackViolation(feedbackId);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.CONFIRMED_HIDDEN, null), actorId));
        });
    }

    public void confirmCommentViolation(@NonNull Long commentId, @NonNull Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        requireAwaitingDecision(commentStatus(commentId), "Comment " + commentId, false);
        tx.executeWithoutResult(status -> {
            feedbackPortFactory.get().confirmCommentViolation(commentId);
            auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.CONFIRMED_HIDDEN, null), actorId));
        });
    }

    public void rejectFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        tx.executeWithoutResult(status -> {
            FeedbackDto existing = feedbackPortFactory.get().findFeedbackById(feedbackId)
                    .orElseThrow(() -> new NoSuchElementException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX));
            FeedbackSnapshotDto snapshot = new FeedbackSnapshotDto(existing.moderationStatus(), existing.moderationReason());
            feedbackPortFactory.get().rejectFeedback(feedbackId);
            auditPortFactory.ifAvailable(p -> p.captureDeletion(feedbackId, snapshot, actorId));
        });
    }

    public void rejectComment(@NonNull Long commentId, @NonNull Long actorId) {
        authorizationService.requireIsPrivileged(actorId);
        tx.executeWithoutResult(status -> {
            FeedbackCommentDto existing = feedbackPortFactory.get().findCommentById(commentId)
                    .orElseThrow(() -> new NoSuchElementException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX));
            FeedbackCommentSnapshotDto snapshot = new FeedbackCommentSnapshotDto(existing.moderationStatus(), existing.moderationReason());
            feedbackPortFactory.get().rejectComment(commentId);
            auditPortFactory.ifAvailable(p -> p.captureDeletion(commentId, snapshot, actorId));
        });
    }

    private FeedbackModerationStatus feedbackStatus(Long feedbackId) {
        return feedbackPortFactory.get().findFeedbackById(feedbackId)
                .orElseThrow(() -> new NoSuchElementException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX))
                .moderationStatus();
    }

    private FeedbackModerationStatus commentStatus(Long commentId) {
        return feedbackPortFactory.get().findCommentById(commentId)
                .orElseThrow(() -> new NoSuchElementException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX))
                .moderationStatus();
    }

    /** A moderator decides only on an entry that was flagged (HIDDEN); an approval may also lift a confirmed violation. */
    private static void requireAwaitingDecision(FeedbackModerationStatus status, String what, boolean allowConfirmed) {
        boolean awaiting = status == FeedbackModerationStatus.HIDDEN
                || (allowConfirmed && status == FeedbackModerationStatus.CONFIRMED_HIDDEN);
        if (!awaiting) {
            throw new FeedbackNotModeratableException(what + " is not awaiting a moderation decision");
        }
    }
}
