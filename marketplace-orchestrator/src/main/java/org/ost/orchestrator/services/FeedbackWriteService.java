package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/** Writes a provider profile's reviews on behalf of a signed-in user: the caller is always the author, an edit only touches the caller's own review, and the reviewed profile must exist. */
@Service
@RequiredArgsConstructor
public class FeedbackWriteService {

    private final FeedbackAccessService feedbackAccessService;
    private final ProviderProfileReadService providerProfileReadService;

    public FeedbackDto create(@NonNull Long actingUserId, @NonNull Long profileId, int rating, @NonNull String text) {
        providerProfileReadService.findById(profileId)
                .orElseThrow(() -> new NoSuchElementException("Provider profile " + profileId + " not found"));
        return feedbackAccessService.save(new FeedbackSaveDto(null, EntityType.PROVIDER_PROFILE, profileId, actingUserId, rating, text, null));
    }

    public FeedbackDto edit(@NonNull Long actingUserId, @NonNull Long feedbackId, int rating, @NonNull String text, Long version) {
        FeedbackDto existing = feedbackAccessService.findFeedbackById(feedbackId)
                .orElseThrow(() -> new NoSuchElementException("Feedback " + feedbackId + " not found"));
        if (!existing.authorId().equals(actingUserId)) {
            throw new AccessDeniedException("User " + actingUserId + " is not the author of feedback " + feedbackId);
        }
        return feedbackAccessService.save(new FeedbackSaveDto(feedbackId, existing.entityType(), existing.entityId(), actingUserId, rating, text, version));
    }
}
