package org.ost.feedback.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackComment;
import org.ost.feedback.entity.FeedbackContent;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.feedback.repository.FeedbackRepository.FeedbackCommentView;
import org.ost.feedback.repository.FeedbackRepository.FeedbackView;
import org.ost.platform.core.EditWindowClosedException;
import org.ost.platform.core.FailureRateLimiter;
import org.ost.platform.core.SelfActionNotAllowedException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.feedback.model.FeedbackEditPolicy;
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
import java.util.function.Function;

/** CRUD for feedback entries and their single reply, sanitization/edit-window enforcement, and aggregate reads/writes. */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class FeedbackService {

    private static final int MAX_FEEDBACK_PER_DAY = 10;
    private static final int MAX_COMMENTS_PER_DAY = 30;

    private final FeedbackRepository repository;
    private final FailureRateLimiter feedbackRateLimiter = new FailureRateLimiter(MAX_FEEDBACK_PER_DAY, Duration.ofDays(1));
    private final FailureRateLimiter commentRateLimiter  = new FailureRateLimiter(MAX_COMMENTS_PER_DAY, Duration.ofDays(1));

    private static final String FEEDBACK_PREFIX = "Feedback ";
    private static final String FEEDBACK_COMMENT_PREFIX = "FeedbackComment ";
    private static final String NOT_FOUND_SUFFIX = " not found";

    // ── Feedback ─────────────────────────────────────────────────────────────

    public List<FeedbackDto> findForEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return toDtoPage(repository.findByEntity(entityType, entityId, pageable), FeedbackService::toDto);
    }

    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.count(entityType, entityId);
    }

    @Transactional
    public FeedbackDto save(@NonNull @Valid FeedbackSaveDto dto) {
        String sanitizedText = HtmlSanitizer.sanitize(dto.feedbackText(), FeedbackSaveDto.TEXT_MAX_LENGTH);
        Feedback saved;
        Long contentId;
        EntityType effectiveEntityType;
        Long effectiveEntityId;
        if (dto.id() != null) {
            FeedbackView existing = repository.findViewById(dto.id())
                    .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + dto.id() + NOT_FOUND_SUFFIX));
            checkWithinEditWindow(existing.createdAt());
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .id(existing.contentId())
                    .contentText(sanitizedText)
                    .moderationStatus(existing.moderationStatus())
                    .moderationReason(existing.moderationReason())
                    .createdAt(existing.createdAt())
                    .version(dto.version())
                    .build());
            contentId = content.getId();
            effectiveEntityType = existing.entityType();
            effectiveEntityId = existing.entityId();
            saved = Feedback.builder()
                    .id(existing.id())
                    .entityType(existing.entityType())
                    .entityId(existing.entityId())
                    .authorId(existing.authorId())
                    .contentId(contentId)
                    .build();
        } else {
            feedbackRateLimiter.checkAllowed(dto.authorId().toString(), "Too many feedback entries submitted today, try again tomorrow");
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .contentText(sanitizedText)
                    .moderationStatus(FeedbackModerationStatus.NEW)
                    .build());
            contentId = content.getId();
            effectiveEntityType = dto.entityType();
            effectiveEntityId = dto.entityId();
            saved = repository.saveLink(Feedback.builder()
                    .entityType(dto.entityType())
                    .entityId(dto.entityId())
                    .authorId(dto.authorId())
                    .contentId(contentId)
                    .build());
            feedbackRateLimiter.recordAttempt(dto.authorId().toString());
        }
        repository.upsertRating(saved.getId(), dto.rating());
        repository.upsertAggregate(effectiveEntityType, effectiveEntityId);
        return toDto(repository.findViewById(saved.getId())
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + saved.getId() + NOT_FOUND_SUFFIX + " after save")));
    }

    public FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.getAggregate(entityType, entityId)
                .map(a -> new FeedbackAggregateDto(a.getEntityType(), a.getEntityId(), a.getAvgRating(), a.getReviewCount()))
                .orElseGet(() -> new FeedbackAggregateDto(entityType, entityId, 0, 0));
    }

    public Optional<FeedbackDto> findFeedbackById(@NonNull Long feedbackId) {
        return repository.findViewById(feedbackId).map(FeedbackService::toDto);
    }

    // ── Comment (single reply) ──────────────────────────────────────────────

    public Optional<FeedbackCommentDto> findCommentByFeedback(@NonNull Long feedbackId) {
        return repository.findCommentByFeedback(feedbackId).map(FeedbackService::toCommentDto);
    }

    public Optional<FeedbackCommentDto> findCommentById(@NonNull Long commentId) {
        return repository.findCommentViewById(commentId).map(FeedbackService::toCommentDto);
    }

    @Transactional
    public FeedbackCommentDto saveComment(@NonNull @Valid FeedbackCommentSaveDto dto) {
        String sanitizedText = HtmlSanitizer.sanitize(dto.commentText(), FeedbackCommentSaveDto.TEXT_MAX_LENGTH);
        FeedbackComment saved;
        if (dto.id() != null) {
            FeedbackCommentView existing = repository.findCommentViewById(dto.id())
                    .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + dto.id() + NOT_FOUND_SUFFIX));
            checkWithinEditWindow(existing.createdAt());
            repository.saveContent(FeedbackContent.builder()
                    .id(existing.contentId())
                    .contentText(sanitizedText)
                    .moderationStatus(existing.moderationStatus())
                    .moderationReason(existing.moderationReason())
                    .createdAt(existing.createdAt())
                    .version(dto.version())
                    .build());
            saved = FeedbackComment.builder()
                    .id(existing.id())
                    .contentId(existing.contentId())
                    .authorId(existing.authorId())
                    .feedbackId(existing.feedbackId())
                    .build();
        } else {
            commentRateLimiter.checkAllowed(dto.authorId().toString(), "Too many comments submitted today, try again tomorrow");
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .contentText(sanitizedText)
                    .moderationStatus(FeedbackModerationStatus.NEW)
                    .build());
            saved = repository.saveCommentLink(FeedbackComment.builder()
                    .contentId(content.getId())
                    .authorId(dto.authorId())
                    .feedbackId(dto.feedbackId())
                    .build());
            commentRateLimiter.recordAttempt(dto.authorId().toString());
        }
        FeedbackCommentView savedView = repository.findCommentViewById(saved.getId())
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + saved.getId() + NOT_FOUND_SUFFIX + " after save"));
        return toCommentDto(savedView);
    }

    @Transactional
    public void deleteComment(@NonNull Long commentId) {
        FeedbackCommentView existing = repository.findCommentViewById(commentId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX));
        checkWithinEditWindow(existing.createdAt());
        repository.deleteCommentHard(commentId, existing.contentId());
    }

    // ── Moderation ───────────────────────────────────────────────────────────

    @Transactional
    public void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId, @NonNull String reason) {
        FeedbackView existing = repository.findViewById(feedbackId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX));
        if (existing.authorId().equals(actorId)) {
            throw new SelfActionNotAllowedException("Cannot flag your own feedback entry");
        }
        repository.flagContent(existing.contentId(), reason);
        repository.upsertAggregate(existing.entityType(), existing.entityId());
    }

    @Transactional
    public void flagComment(@NonNull Long commentId, @NonNull Long actorId, @NonNull String reason) {
        FeedbackCommentView existing = repository.findCommentViewById(commentId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX));
        if (existing.authorId().equals(actorId)) {
            throw new SelfActionNotAllowedException("Cannot flag your own comment");
        }
        repository.flagContent(existing.contentId(), reason);
    }

    @Transactional
    public void approveFeedback(@NonNull Long feedbackId) {
        FeedbackView existing = repository.findViewById(feedbackId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX));
        repository.updateModerationStatus(existing.contentId(), FeedbackModerationStatus.NEW);
        repository.upsertAggregate(existing.entityType(), existing.entityId());
    }

    @Transactional
    public void approveComment(@NonNull Long commentId) {
        FeedbackCommentView existing = repository.findCommentViewById(commentId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX));
        repository.updateModerationStatus(existing.contentId(), FeedbackModerationStatus.NEW);
    }

    @Transactional
    public void confirmFeedbackViolation(@NonNull Long feedbackId) {
        FeedbackView existing = repository.findViewById(feedbackId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX));
        repository.updateModerationStatus(existing.contentId(), FeedbackModerationStatus.CONFIRMED_HIDDEN);
    }

    @Transactional
    public void confirmCommentViolation(@NonNull Long commentId) {
        FeedbackCommentView existing = repository.findCommentViewById(commentId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX));
        repository.updateModerationStatus(existing.contentId(), FeedbackModerationStatus.CONFIRMED_HIDDEN);
    }

    @Transactional
    public void rejectFeedback(@NonNull Long feedbackId) {
        FeedbackView existing = repository.findViewById(feedbackId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX));
        repository.deleteFeedbackHard(feedbackId, existing.contentId());
        repository.upsertAggregate(existing.entityType(), existing.entityId());
    }

    /** Admin reject bypasses the edit window {@link #deleteComment} enforces. */
    @Transactional
    public void rejectComment(@NonNull Long commentId) {
        FeedbackCommentView existing = repository.findCommentViewById(commentId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_COMMENT_PREFIX + commentId + NOT_FOUND_SUFFIX));
        repository.deleteCommentHard(commentId, existing.contentId());
    }

    @Transactional(readOnly = true)
    public List<FeedbackDto> findHiddenFeedback(@NonNull Pageable pageable) {
        return toDtoPage(repository.findHiddenFeedback(pageable), FeedbackService::toDto);
    }

    @Transactional(readOnly = true)
    public List<FeedbackCommentDto> findHiddenComments(@NonNull Pageable pageable) {
        return toDtoPage(repository.findHiddenComments(pageable), FeedbackService::toCommentDto);
    }

    @Transactional(readOnly = true)
    public List<FeedbackDto> findConfirmedViolationFeedback(@NonNull Pageable pageable) {
        return toDtoPage(repository.findConfirmedViolationFeedback(pageable), FeedbackService::toDto);
    }

    @Transactional(readOnly = true)
    public List<FeedbackCommentDto> findConfirmedViolationComments(@NonNull Pageable pageable) {
        return toDtoPage(repository.findConfirmedViolationComments(pageable), FeedbackService::toCommentDto);
    }

    private static <R, D> List<D> toDtoPage(List<R> rows, Function<R, D> mapper) {
        return rows.stream().map(mapper).toList();
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static void checkWithinEditWindow(Instant createdAt) {
        if (createdAt != null && Instant.now().isAfter(createdAt.plus(FeedbackEditPolicy.MAX_EDIT_AGE))) {
            throw new EditWindowClosedException("Edit window has closed");
        }
    }

    private static FeedbackDto toDto(FeedbackView view) {
        return new FeedbackDto(view.id(), view.entityType(), view.entityId(), view.authorId(),
                view.rating(), view.feedbackText(), view.moderationStatus(), view.moderationReason(),
                view.createdAt(), view.updatedAt(), view.version());
    }

    private static FeedbackCommentDto toCommentDto(FeedbackCommentView view) {
        return new FeedbackCommentDto(view.id(), view.feedbackId(), view.authorId(),
                view.commentText(), view.moderationStatus(), view.moderationReason(),
                view.createdAt(), view.updatedAt(), view.version());
    }
}
