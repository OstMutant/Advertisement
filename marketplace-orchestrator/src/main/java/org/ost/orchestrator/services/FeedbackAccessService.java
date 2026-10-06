package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSnapshotDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.dto.FeedbackSnapshotDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Wraps {@link FeedbackPort} for marketplace-app, degrading gracefully when feedback-spring-boot-starter is absent. */
@Service
@RequiredArgsConstructor
public class FeedbackAccessService {

    private final ComponentFactory<FeedbackPort> feedbackPortFactory;
    private final ComponentFactory<AuditPort>    auditPortFactory;
    private final UserActorNameService           userActorNameService;

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

    public List<FeedbackCommentDto> findCommentsByFeedback(@NonNull Long feedbackId, Long viewerId) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findCommentsByFeedback(feedbackId, viewerId))
                .orElse(List.of());
    }

    public FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto) {
        return feedbackPortFactory.get().saveComment(dto);
    }

    public void saveReaction(@NonNull FeedbackCommentReactionSaveDto dto) {
        feedbackPortFactory.get().saveReaction(dto);
    }

    public void deleteComment(@NonNull Long commentId) {
        feedbackPortFactory.get().deleteComment(commentId);
    }

    public Map<Long, String> resolveAuthorNames(@NonNull Set<Long> authorIds) {
        return userActorNameService.resolveNames(authorIds);
    }

    public List<FeedbackDto> findHiddenFeedback(@NonNull Pageable pageable) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findHiddenFeedback(pageable))
                .orElse(List.of());
    }

    public List<FeedbackCommentDto> findHiddenComments(@NonNull Pageable pageable) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findHiddenComments(pageable))
                .orElse(List.of());
    }

    public void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        feedbackPortFactory.get().flagFeedback(feedbackId, actorId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN), actorId));
    }

    public void flagComment(@NonNull Long commentId, @NonNull Long actorId) {
        feedbackPortFactory.get().flagComment(commentId, actorId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN), actorId));
    }

    public void approveFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        feedbackPortFactory.get().approveFeedback(feedbackId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.NEW), actorId));
    }

    public void approveComment(@NonNull Long commentId, @NonNull Long actorId) {
        feedbackPortFactory.get().approveComment(commentId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.NEW), actorId));
    }

    public void rejectFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        FeedbackDto existing = feedbackPortFactory.get().findFeedbackById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("Feedback " + feedbackId + " not found"));
        FeedbackSnapshotDto snapshot = new FeedbackSnapshotDto(existing.moderationStatus());
        feedbackPortFactory.get().rejectFeedback(feedbackId);
        auditPortFactory.ifAvailable(p -> p.captureDeletion(feedbackId, snapshot, actorId));
    }

    public void rejectComment(@NonNull Long commentId, @NonNull Long actorId) {
        FeedbackCommentDto existing = feedbackPortFactory.get().findCommentById(commentId)
                .orElseThrow(() -> new IllegalStateException("FeedbackComment " + commentId + " not found"));
        FeedbackCommentSnapshotDto snapshot = new FeedbackCommentSnapshotDto(existing.moderationStatus());
        feedbackPortFactory.get().rejectComment(commentId);
        auditPortFactory.ifAvailable(p -> p.captureDeletion(commentId, snapshot, actorId));
    }
}
