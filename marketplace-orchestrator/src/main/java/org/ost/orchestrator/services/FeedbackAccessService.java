package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Wraps {@link FeedbackPort} for marketplace-app, degrading gracefully when feedback-spring-boot-starter is absent. */
@Service
@RequiredArgsConstructor
public class FeedbackAccessService {

    private final ComponentFactory<FeedbackPort> feedbackPortFactory;
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
}
