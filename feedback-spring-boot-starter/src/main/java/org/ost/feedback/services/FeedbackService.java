package org.ost.feedback.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.feedback.entity.Feedback;
import org.ost.feedback.entity.FeedbackComment;
import org.ost.feedback.entity.FeedbackContent;
import org.ost.feedback.repository.FeedbackRepository;
import org.ost.feedback.repository.FeedbackRepository.FeedbackCommentReactionView;
import org.ost.feedback.repository.FeedbackRepository.FeedbackCommentView;
import org.ost.feedback.repository.FeedbackRepository.FeedbackView;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** CRUD for feedback entries and their comment tree, sanitization/edit-window enforcement, and aggregate reads/writes. */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class FeedbackService {

    private static final Duration EDIT_WINDOW = Duration.ofHours(48);

    private final FeedbackRepository repository;

    // ── Feedback ─────────────────────────────────────────────────────────────

    public List<FeedbackDto> findForEntity(@NonNull EntityType entityType, @NonNull Long entityId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return repository.findByEntity(entityType, entityId, pageable).stream().map(FeedbackService::toDto).toList();
    }

    public int count(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.count(entityType, entityId);
    }

    @Transactional
    public FeedbackDto save(@NonNull @Valid FeedbackSaveDto dto) {
        String sanitizedText = HtmlSanitizer.sanitize(dto.feedbackText(), FeedbackSaveDto.TEXT_MAX_LENGTH);
        Feedback saved;
        Long contentId;
        if (dto.id() != null) {
            FeedbackView existing = repository.findViewById(dto.id())
                    .orElseThrow(() -> new IllegalStateException("Feedback " + dto.id() + " not found"));
            checkWithinEditWindow(existing.createdAt());
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .id(existing.contentId())
                    .contentText(sanitizedText)
                    .moderationStatus(existing.moderationStatus())
                    .createdAt(existing.createdAt())
                    .version(dto.version())
                    .build());
            contentId = content.getId();
            saved = Feedback.builder()
                    .id(existing.id())
                    .entityType(dto.entityType())
                    .entityId(dto.entityId())
                    .authorId(dto.authorId())
                    .contentId(contentId)
                    .build();
        } else {
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .contentText(sanitizedText)
                    .moderationStatus(FeedbackModerationStatus.NEW)
                    .build());
            contentId = content.getId();
            saved = repository.saveLink(Feedback.builder()
                    .entityType(dto.entityType())
                    .entityId(dto.entityId())
                    .authorId(dto.authorId())
                    .contentId(contentId)
                    .build());
        }
        repository.upsertRating(saved.getId(), dto.rating());
        repository.upsertAggregate(dto.entityType(), dto.entityId());
        return toDto(repository.findViewById(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Feedback " + saved.getId() + " not found after save")));
    }

    public FeedbackAggregateDto getAggregate(@NonNull EntityType entityType, @NonNull Long entityId) {
        return repository.getAggregate(entityType, entityId)
                .map(a -> new FeedbackAggregateDto(a.getEntityType(), a.getEntityId(), a.getAvgRating(), a.getReviewCount()))
                .orElseGet(() -> new FeedbackAggregateDto(entityType, entityId, 0, 0));
    }

    // ── Comments ─────────────────────────────────────────────────────────────

    public List<FeedbackCommentDto> findCommentsByFeedback(@NonNull Long feedbackId, Long viewerId) {
        List<FeedbackCommentView> views = repository.findCommentsByFeedback(feedbackId);
        List<Long> commentIds = views.stream().map(FeedbackCommentView::id).toList();
        Map<Long, Map<String, List<Long>>> reactionsByComment = groupReactions(repository.findReactionsByComments(commentIds));
        return views.stream().map(view -> toCommentDto(view, reactionsByComment, viewerId)).toList();
    }

    @Transactional
    public FeedbackCommentDto saveComment(@NonNull @Valid FeedbackCommentSaveDto dto) {
        String sanitizedText = HtmlSanitizer.sanitize(dto.commentText(), FeedbackCommentSaveDto.TEXT_MAX_LENGTH);
        FeedbackComment saved;
        if (dto.id() != null) {
            FeedbackCommentView existing = repository.findCommentViewById(dto.id())
                    .orElseThrow(() -> new IllegalStateException("FeedbackComment " + dto.id() + " not found"));
            checkWithinEditWindow(existing.createdAt());
            repository.saveContent(FeedbackContent.builder()
                    .id(existing.contentId())
                    .contentText(sanitizedText)
                    .moderationStatus(existing.moderationStatus())
                    .createdAt(existing.createdAt())
                    .version(dto.version())
                    .build());
            saved = FeedbackComment.builder()
                    .id(existing.id())
                    .contentId(existing.contentId())
                    .authorId(existing.authorId())
                    .feedbackId(existing.feedbackId())
                    .parentCommentId(existing.parentCommentId())
                    .build();
        } else {
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .contentText(sanitizedText)
                    .moderationStatus(FeedbackModerationStatus.NEW)
                    .build());
            saved = repository.saveCommentLink(FeedbackComment.builder()
                    .contentId(content.getId())
                    .authorId(dto.authorId())
                    .feedbackId(dto.feedbackId())
                    .parentCommentId(dto.parentCommentId())
                    .build());
        }
        FeedbackCommentView savedView = repository.findCommentViewById(saved.getId())
                .orElseThrow(() -> new IllegalStateException("FeedbackComment " + saved.getId() + " not found after save"));
        Map<Long, Map<String, List<Long>>> reactions = groupReactions(repository.findReactionsByComments(List.of(saved.getId())));
        return toCommentDto(savedView, reactions, dto.authorId());
    }

    @Transactional
    public void saveReaction(@NonNull @Valid FeedbackCommentReactionSaveDto dto) {
        Optional<String> existing = repository.findReactionType(dto.feedbackCommentId(), dto.userId());
        if (existing.isPresent() && existing.get().equals(dto.reactionType().name())) {
            repository.deleteReaction(dto.feedbackCommentId(), dto.userId());
        } else {
            repository.upsertReaction(dto.feedbackCommentId(), dto.userId(), dto.reactionType().name());
        }
    }

    @Transactional
    public void deleteComment(@NonNull Long commentId) {
        FeedbackCommentView existing = repository.findCommentViewById(commentId)
                .orElseThrow(() -> new IllegalStateException("FeedbackComment " + commentId + " not found"));
        checkWithinEditWindow(existing.createdAt());
        if (repository.countChildren(commentId) == 0) {
            repository.deleteCommentHard(commentId, existing.contentId());
            pruneDanglingTombstones(existing.parentCommentId());
        } else {
            repository.tombstoneComment(existing.contentId());
        }
    }

    /** Walks up the parent chain, hard-deleting any ancestor that is itself tombstoned and now has
     *  zero children left -- otherwise a tombstone whose last reply was removed lingers forever
     *  with nothing left for it to thread together. */
    private void pruneDanglingTombstones(Long parentCommentId) {
        Long current = parentCommentId;
        while (current != null) {
            FeedbackCommentView parent = repository.findCommentViewById(current).orElse(null);
            if (parent == null || parent.commentText() != null || repository.countChildren(current) > 0) {
                return;
            }
            repository.deleteCommentHard(current, parent.contentId());
            current = parent.parentCommentId();
        }
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private static void checkWithinEditWindow(Instant createdAt) {
        if (createdAt != null && Instant.now().isAfter(createdAt.plus(EDIT_WINDOW))) {
            throw new IllegalStateException("Edit window has closed");
        }
    }

    private static Map<Long, Map<String, List<Long>>> groupReactions(List<FeedbackCommentReactionView> reactions) {
        Map<Long, Map<String, List<Long>>> result = new HashMap<>();
        for (FeedbackCommentReactionView reaction : reactions) {
            result.computeIfAbsent(reaction.feedbackCommentId(), _ -> new HashMap<>())
                    .computeIfAbsent(reaction.reactionType(), _ -> new ArrayList<>())
                    .add(reaction.userId());
        }
        return result;
    }

    private static FeedbackDto toDto(FeedbackView view) {
        return new FeedbackDto(view.id(), view.entityType(), view.entityId(), view.authorId(),
                view.rating(), view.feedbackText(), view.moderationStatus(),
                view.createdAt(), view.updatedAt(), view.version());
    }

    private static FeedbackCommentDto toCommentDto(FeedbackCommentView view, Map<Long, Map<String, List<Long>>> reactionsByComment, Long viewerId) {
        Map<String, List<Long>> reactorIdsByType = reactionsByComment.getOrDefault(view.id(), Map.of());
        String myReaction = viewerId == null ? null : reactorIdsByType.entrySet().stream()
                .filter(entry -> entry.getValue().contains(viewerId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        return new FeedbackCommentDto(view.id(), view.feedbackId(), view.parentCommentId(), view.authorId(),
                view.commentText(), view.moderationStatus(), view.createdAt(), view.updatedAt(), view.version(),
                reactorIdsByType, myReaction);
    }
}
