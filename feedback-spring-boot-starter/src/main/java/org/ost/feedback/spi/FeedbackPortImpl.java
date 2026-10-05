package org.ost.feedback.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.feedback.services.FeedbackService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Pure delegation to {@link FeedbackService} -- no business logic of its own, per this project's
 *  {@code *PortImpl} convention. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackPortImpl implements FeedbackPort {

    private final FeedbackService service;

    @Override
    public List<FeedbackDto> findByEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size) {
        return service.findForEntity(entityType, entityId, page, size);
    }

    @Override
    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return service.count(entityType, entityId);
    }

    @Override
    @Transactional
    public FeedbackDto save(@NonNull FeedbackSaveDto dto) {
        return service.save(dto);
    }

    @Override
    public FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return service.getAggregate(entityType, entityId);
    }

    @Override
    public List<FeedbackCommentDto> findCommentsByFeedback(@NonNull Long feedbackId, Long viewerId) {
        return service.findCommentsByFeedback(feedbackId, viewerId);
    }

    @Override
    @Transactional
    public FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto) {
        return service.saveComment(dto);
    }

    @Override
    @Transactional
    public void saveReaction(@NonNull FeedbackCommentReactionSaveDto dto) {
        service.saveReaction(dto);
    }

    @Override
    @Transactional
    public void deleteComment(@NonNull Long commentId) {
        service.deleteComment(commentId);
    }

    @Override
    @Transactional
    public void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        service.flagFeedback(feedbackId, actorId);
    }

    @Override
    @Transactional
    public void flagComment(@NonNull Long commentId, @NonNull Long actorId) {
        service.flagComment(commentId, actorId);
    }

    @Override
    @Transactional
    public void approveFeedback(@NonNull Long feedbackId) {
        service.approveFeedback(feedbackId);
    }

    @Override
    @Transactional
    public void approveComment(@NonNull Long commentId) {
        service.approveComment(commentId);
    }

    @Override
    @Transactional
    public void rejectFeedback(@NonNull Long feedbackId) {
        service.rejectFeedback(feedbackId);
    }

    @Override
    @Transactional
    public void rejectComment(@NonNull Long commentId) {
        service.rejectComment(commentId);
    }

    @Override
    public List<FeedbackDto> findHiddenFeedback(@NonNull Pageable pageable) {
        return service.findHiddenFeedback(pageable);
    }

    @Override
    public List<FeedbackCommentDto> findHiddenComments(@NonNull Pageable pageable) {
        return service.findHiddenComments(pageable);
    }

    @Override
    public Optional<FeedbackDto> findFeedbackById(@NonNull Long feedbackId) {
        return service.findFeedbackById(feedbackId);
    }

    @Override
    public Optional<FeedbackCommentDto> findCommentById(@NonNull Long commentId) {
        return service.findCommentById(commentId);
    }
}
