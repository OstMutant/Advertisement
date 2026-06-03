package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.audit.repository.AuditLogProjection;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AuditReadService {

    private final AuditLogRepository      repository;
    private final AuditActivityEnrichHook activityEnrichHook;

    // ── History ───────────────────────────────────────────────────────────────

    public List<AuditHistoryItemDto> getEntityHistory(EntityType entityType, Long entityId,
                                                      Long currentUserId, boolean showAll) {
        List<AuditHistoryItemDto> history = repository
                .findRows(entityType, entityId, showAll ? null : currentUserId)
                .stream().limit(100).map(this::toHistoryItem).toList();

        return history.stream()
                .map(h -> {
                    List<ChangeEntry> mediaChanges = activityEnrichHook.getAdditionalChanges(
                            new EntityRef(entityType, entityId), h.version());
                    if (mediaChanges.isEmpty()) return h;
                    List<ChangeEntry> combined = new ArrayList<>(mediaChanges);
                    combined.addAll(h.changes());
                    return h.withChanges(combined);
                })
                .toList();
    }

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return repository.getLastSnapshot(entityType, entityId);
    }

    // ── Activity ──────────────────────────────────────────────────────────────

    public List<AuditActivityItemDto> getForSubject(List<EntityRef> subjects, Long actorId) {
        Stream<AuditLogProjection> subjectRows = subjects.stream()
                .flatMap(s -> repository.findRows(s.entityType(), s.entityId(), null).stream());
        Stream<AuditLogProjection> actorRows = actorId != null
                ? repository.findRowsByActor(actorId).stream()
                : Stream.empty();

        List<AuditActivityItemDto> items = Stream.concat(subjectRows, actorRows)
                .collect(Collectors.toMap(AuditLogProjection::id, r -> r, (a, _) -> a))
                .values().stream()
                .sorted(Comparator.comparing(AuditLogProjection::createdAt).reversed())
                .limit(20)
                .map(this::toActivityItem)
                .toList();

        return activityEnrichHook.merge(subjects, items);
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AuditHistoryItemDto toHistoryItem(AuditLogProjection row) {
        return new AuditHistoryItemDto(
                row.id(), row.version(), row.actionType(), row.actorId(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.prevId(), row.snapshot(), row.prevSnapshot());
    }

    private AuditActivityItemDto toActivityItem(AuditLogProjection row) {
        return new AuditActivityItemDto(
                row.id(), row.entityId(), row.entityType(), row.actionType(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.actorId(), row.snapshot());
    }
}
