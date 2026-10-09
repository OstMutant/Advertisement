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
import org.ost.orchestrator.dto.ModerationQueueFilterDto;
import org.ost.orchestrator.dto.ModerationQueueItemDto;
import org.ost.orchestrator.dto.ModerationQueueItemType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public List<FeedbackDto> findConfirmedViolationFeedback(@NonNull Pageable pageable) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findConfirmedViolationFeedback(pageable))
                .orElse(List.of());
    }

    public List<FeedbackCommentDto> findConfirmedViolationComments(@NonNull Pageable pageable) {
        return feedbackPortFactory.findIfAvailable()
                .map(port -> port.findConfirmedViolationComments(pageable))
                .orElse(List.of());
    }

    private static final int MAX_QUEUE_FETCH = 1000;

    /** One page of the merged moderation queue, filtered and sorted per request, plus its true total count -- paged in memory since queue size is bounded by real reports, not catalog scale. */
    public record ModerationQueuePage(List<ModerationQueueItemDto> items, int totalCount) {}

    public ModerationQueuePage findModerationQueue(@NonNull ModerationQueueFilterDto filter, @NonNull Sort sort, int page, int size) {
        List<ModerationQueueItemDto> all = buildFullModerationQueue().stream()
                .filter(item -> matchesFilter(item, filter))
                .sorted(resolveComparator(sort))
                .toList();
        int fromIndex = Math.min(page * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        return new ModerationQueuePage(all.subList(fromIndex, toIndex), all.size());
    }

    private boolean matchesFilter(ModerationQueueItemDto item, ModerationQueueFilterDto filter) {
        if (filter.getTypes() != null && !filter.getTypes().contains(item.getType())) return false;
        if (filter.getCreatedAtStart() != null && item.getCreatedAt().isBefore(filter.getCreatedAtStart())) return false;
        if (filter.getCreatedAtEnd() != null && item.getCreatedAt().isAfter(filter.getCreatedAtEnd())) return false;
        if (filter.getAuthorName() != null && !containsIgnoreCase(item.getAuthorName(), filter.getAuthorName())) return false;
        if (filter.getReasonText() != null && !containsIgnoreCase(item.getModerationReason(), filter.getReasonText())) return false;
        if (filter.getStatuses() == null || filter.getStatuses().isEmpty()) {
            if (item.getModerationStatus() != FeedbackModerationStatus.HIDDEN) return false;
        } else if (!filter.getStatuses().contains(item.getModerationStatus())) {
            return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private Comparator<ModerationQueueItemDto> resolveComparator(Sort sort) {
        Sort.Order order = sort.get().findFirst().orElse(Sort.Order.desc(ModerationQueueItemDto.Fields.createdAt));
        Comparator<ModerationQueueItemDto> comparator = switch (order.getProperty()) {
            case ModerationQueueItemDto.Fields.authorName -> Comparator.comparing(ModerationQueueItemDto::getAuthorName, String.CASE_INSENSITIVE_ORDER);
            case ModerationQueueItemDto.Fields.type -> Comparator.comparing(i -> i.getType().name());
            default -> Comparator.comparing(ModerationQueueItemDto::getCreatedAt);
        };
        return order.isDescending() ? comparator.reversed() : comparator;
    }

    private List<ModerationQueueItemDto> buildFullModerationQueue() {
        List<FeedbackDto> hiddenFeedback = findHiddenFeedback(PageRequest.of(0, MAX_QUEUE_FETCH));
        List<FeedbackCommentDto> hiddenComments = findHiddenComments(PageRequest.of(0, MAX_QUEUE_FETCH));
        List<FeedbackDto> confirmedFeedback = findConfirmedViolationFeedback(PageRequest.of(0, MAX_QUEUE_FETCH));
        List<FeedbackCommentDto> confirmedComments = findConfirmedViolationComments(PageRequest.of(0, MAX_QUEUE_FETCH));

        Set<Long> authorIds = new HashSet<>();
        hiddenFeedback.forEach(f -> authorIds.add(f.authorId()));
        hiddenComments.forEach(c -> authorIds.add(c.authorId()));
        confirmedFeedback.forEach(f -> authorIds.add(f.authorId()));
        confirmedComments.forEach(c -> authorIds.add(c.authorId()));
        Map<Long, String> names = authorIds.isEmpty() ? Map.of() : resolveAuthorNames(authorIds);

        List<ModerationQueueItemDto> items = new ArrayList<>();
        hiddenFeedback.forEach(f -> items.add(toQueueItem(f, names, FeedbackModerationStatus.HIDDEN)));
        hiddenComments.forEach(c -> items.add(toQueueItem(c, names, FeedbackModerationStatus.HIDDEN)));
        confirmedFeedback.forEach(f -> items.add(toQueueItem(f, names, FeedbackModerationStatus.CONFIRMED_HIDDEN)));
        confirmedComments.forEach(c -> items.add(toQueueItem(c, names, FeedbackModerationStatus.CONFIRMED_HIDDEN)));
        return items;
    }

    private ModerationQueueItemDto toQueueItem(FeedbackDto f, Map<Long, String> names, FeedbackModerationStatus status) {
        return ModerationQueueItemDto.builder()
                .type(ModerationQueueItemType.FEEDBACK)
                .id(f.id())
                .entityId(f.entityId())
                .contentText(f.feedbackText())
                .rating(f.rating())
                .moderationReason(f.moderationReason())
                .authorId(f.authorId())
                .authorName(names.getOrDefault(f.authorId(), String.valueOf(f.authorId())))
                .createdAt(f.createdAt())
                .moderationStatus(status)
                .build();
    }

    private ModerationQueueItemDto toQueueItem(FeedbackCommentDto c, Map<Long, String> names, FeedbackModerationStatus status) {
        Long entityId = findFeedbackById(c.feedbackId()).map(FeedbackDto::entityId).orElse(null);
        return ModerationQueueItemDto.builder()
                .type(ModerationQueueItemType.COMMENT)
                .id(c.id())
                .entityId(entityId)
                .contentText(c.commentText())
                .rating(null)
                .moderationReason(c.moderationReason())
                .authorId(c.authorId())
                .authorName(names.getOrDefault(c.authorId(), String.valueOf(c.authorId())))
                .createdAt(c.createdAt())
                .moderationStatus(status)
                .build();
    }

    public void flagFeedback(@NonNull Long feedbackId, @NonNull Long actorId, @NonNull String reason) {
        feedbackPortFactory.get().flagFeedback(feedbackId, actorId, reason);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.HIDDEN, reason), actorId));
    }

    public void flagComment(@NonNull Long commentId, @NonNull Long actorId, @NonNull String reason) {
        feedbackPortFactory.get().flagComment(commentId, actorId, reason);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.HIDDEN, reason), actorId));
    }

    public void approveFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        feedbackPortFactory.get().approveFeedback(feedbackId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.NEW, null), actorId));
    }

    public void approveComment(@NonNull Long commentId, @NonNull Long actorId) {
        feedbackPortFactory.get().approveComment(commentId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.NEW, null), actorId));
    }

    public void confirmFeedbackViolation(@NonNull Long feedbackId, @NonNull Long actorId) {
        feedbackPortFactory.get().confirmFeedbackViolation(feedbackId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(feedbackId, new FeedbackSnapshotDto(FeedbackModerationStatus.CONFIRMED_HIDDEN, null), actorId));
    }

    public void confirmCommentViolation(@NonNull Long commentId, @NonNull Long actorId) {
        feedbackPortFactory.get().confirmCommentViolation(commentId);
        auditPortFactory.ifAvailable(p -> p.captureUpdate(commentId, new FeedbackCommentSnapshotDto(FeedbackModerationStatus.CONFIRMED_HIDDEN, null), actorId));
    }

    public void rejectFeedback(@NonNull Long feedbackId, @NonNull Long actorId) {
        FeedbackDto existing = feedbackPortFactory.get().findFeedbackById(feedbackId)
                .orElseThrow(() -> new IllegalStateException("Feedback " + feedbackId + " not found"));
        FeedbackSnapshotDto snapshot = new FeedbackSnapshotDto(existing.moderationStatus(), existing.moderationReason());
        feedbackPortFactory.get().rejectFeedback(feedbackId);
        auditPortFactory.ifAvailable(p -> p.captureDeletion(feedbackId, snapshot, actorId));
    }

    public void rejectComment(@NonNull Long commentId, @NonNull Long actorId) {
        FeedbackCommentDto existing = feedbackPortFactory.get().findCommentById(commentId)
                .orElseThrow(() -> new IllegalStateException("FeedbackComment " + commentId + " not found"));
        FeedbackCommentSnapshotDto snapshot = new FeedbackCommentSnapshotDto(existing.moderationStatus(), existing.moderationReason());
        feedbackPortFactory.get().rejectComment(commentId);
        auditPortFactory.ifAvailable(p -> p.captureDeletion(commentId, snapshot, actorId));
    }
}
