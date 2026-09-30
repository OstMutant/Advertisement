package org.ost.feedback.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.sanitizer.HtmlSanitizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** CRUD for {@code feedback} with sanitization/edit-window enforcement, and aggregate reads/writes for {@code feedback_aggregate}. */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class FeedbackService {

    private static final Duration EDIT_WINDOW = Duration.ofHours(48);

    private final FeedbackRepository repository;

    public List<FeedbackDto> findForEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByEntity(entityType, entityId, pageable).stream().map(FeedbackService::toDto).toList();
    }

    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.count(entityType, entityId);
    }

    @Transactional
    public FeedbackDto save(@NonNull @Valid FeedbackSaveDto dto) {
        Optional<Feedback> existing = repository.findByAuthorAndEntity(dto.authorId(), dto.entityType(), dto.entityId());
        existing.ifPresent(FeedbackService::checkWithinEditWindow);
        String sanitizedText = HtmlSanitizer.sanitize(dto.feedbackText(), FeedbackSaveDto.TEXT_MAX_LENGTH);
        Feedback entity = buildEntity(dto, sanitizedText, existing.orElse(null));
        Feedback saved = repository.save(entity);
        repository.upsertAggregate(dto.entityType(), dto.entityId());
        return toDto(saved);
    }

    public FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.getAggregate(entityType, entityId)
                .map(a -> new FeedbackAggregateDto(a.getEntityType(), a.getEntityId(), a.getAvgRating(), a.getReviewCount()))
                .orElseGet(() -> new FeedbackAggregateDto(entityType, entityId, 0, 0));
    }

    private static void checkWithinEditWindow(Feedback existing) {
        if (existing.getCreatedAt() != null && Instant.now().isAfter(existing.getCreatedAt().plus(EDIT_WINDOW))) {
            throw new IllegalStateException("Feedback " + existing.getId() + " edit window has closed");
        }
    }

    private static Feedback buildEntity(FeedbackSaveDto dto, String sanitizedText, Feedback before) {
        return Feedback.builder()
                .id(before != null ? before.getId() : null)
                .entityType(dto.entityType())
                .entityId(dto.entityId())
                .authorId(dto.authorId())
                .rating(dto.rating())
                .feedbackText(sanitizedText)
                .moderationStatus(before != null ? before.getModerationStatus() : FeedbackModerationStatus.NEW)
                .createdAt(before != null ? before.getCreatedAt() : null)
                .version(dto.version())
                .build();
    }

    private static FeedbackDto toDto(Feedback entity) {
        return new FeedbackDto(entity.getId(), entity.getEntityType(), entity.getEntityId(), entity.getAuthorId(),
                entity.getRating(), entity.getFeedbackText(), entity.getModerationStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
}
