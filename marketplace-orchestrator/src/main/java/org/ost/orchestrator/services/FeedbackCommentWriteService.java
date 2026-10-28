package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * Enforces that only the reviewed provider profile's own owner may write the single reply to one
 * of its feedback entries. The owner-check lives here, not in feedback-spring-boot-starter (a
 * starter must not depend on ProviderProfilePort) and not in FeedbackAccessService (adding a
 * third *Port dependency there would exceed the orchestrator's 2-port composition limit).
 */
@Service
@RequiredArgsConstructor
public class FeedbackCommentWriteService {

    private final FeedbackAccessService      feedbackAccessService;
    private final ProviderProfileReadService providerProfileReadService;
    private final AuthorizationService       authorizationService;

    private static final String NOT_FOUND_SUFFIX = " not found";

    public FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto) {
        if (dto.id() == null) {
            requireOwner(dto.feedbackId(), dto.authorId());
        } else {
            requireCommentAuthor(dto.id(), dto.authorId());
        }
        return feedbackAccessService.saveComment(dto);
    }

    public void deleteComment(@NonNull Long commentId, @NonNull Long actingUserId) {
        requireCommentAuthor(commentId, actingUserId);
        feedbackAccessService.deleteComment(commentId);
    }

    private void requireOwner(Long feedbackId, Long actingUserId) {
        FeedbackDto feedback = feedbackAccessService.findFeedbackById(feedbackId)
                .orElseThrow(() -> new NoSuchElementException("Feedback " + feedbackId + NOT_FOUND_SUFFIX));
        if (feedback.entityType() != EntityType.PROVIDER_PROFILE) {
            throw new AccessDeniedException("Feedback " + feedbackId + " is not attached to a provider profile");
        }
        ProviderProfileDto profile = providerProfileReadService.findById(feedback.entityId())
                .orElseThrow(() -> new NoSuchElementException("ProviderProfile " + feedback.entityId() + NOT_FOUND_SUFFIX));
        if (!authorizationService.isOwner(actingUserId, profile.getActorId())) {
            throw new AccessDeniedException("User " + actingUserId + " is not the owner of provider profile " + profile.getActorId());
        }
    }

    private void requireCommentAuthor(Long commentId, Long actingUserId) {
        FeedbackCommentDto comment = feedbackAccessService.findCommentById(commentId)
                .orElseThrow(() -> new NoSuchElementException("FeedbackComment " + commentId + NOT_FOUND_SUFFIX));
        if (!comment.authorId().equals(actingUserId)) {
            throw new AccessDeniedException("User " + actingUserId + " is not the author of comment " + commentId);
        }
    }
}
