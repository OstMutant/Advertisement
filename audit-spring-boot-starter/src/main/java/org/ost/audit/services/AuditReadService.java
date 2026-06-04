package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditReadService {

    private final AuditLogRepository            repository;
    private final List<AuditActivityEnrichHook> activityEnrichHooks;

    // ── History ───────────────────────────────────────────────────────────────

    public List<AuditHistoryItemDto> getEntityHistory(EntityType entityType, Long entityId,
                                                      Long currentUserId, boolean showAll) {
        List<AuditHistoryItemDto> history = repository
                .findRows(entityType, entityId, showAll ? null : currentUserId, 100)
                .stream().map(this::toHistoryItem).toList();

        return history.stream()
                .map(h -> {
                    EntityRef ref = new EntityRef(entityType, entityId);
                    List<ChangeEntry> mediaChanges = activityEnrichHooks.stream()
                            .filter(hook -> hook.entityType() == entityType)
                            .findFirst()
                            .map(hook -> hook.getAdditionalChanges(ref, h.version()))
                            .orElse(List.of());
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

    public List<AuditActivityItemDto<AuditableSnapshot>> getForSubject(List<EntityRef> subjects, Long actorId, int limit) {
        Stream<AuditLogProjection> subjectRows = subjects.stream()
                .flatMap(s -> repository.findRows(s.entityType(), s.entityId(), null, limit).stream());
        Stream<AuditLogProjection> actorRows = actorId != null
                ? repository.findByActor(actorId, limit).stream()
                : Stream.empty();

        List<AuditActivityItemDto<AuditableSnapshot>> items = Stream.concat(subjectRows, actorRows)
                .collect(Collectors.toMap(AuditLogProjection::id, r -> r, (a, _) -> a))
                .values().stream()
                .sorted(Comparator.comparing(AuditLogProjection::createdAt).reversed())
                .limit(limit)
                .map(this::toActivityItem)
                .toList();

        List<AuditActivityItemDto<AuditableSnapshot>> merged = items;
        for (AuditActivityEnrichHook hook : activityEnrichHooks) {
            merged = hook.merge(subjects, merged);
        }
        return merged;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AuditHistoryItemDto toHistoryItem(AuditLogProjection row) {
        if (row.snapshot() == null) {
            log.warn("Audit row id={} has null snapshot, rendering with empty changes", row.id());
            return new AuditHistoryItemDto(row.id(), row.version(), row.actionType(), row.actorId(), row.createdAt(),
                    List.of(), row.prevId(), null, row.prevSnapshot());
        }
        return new AuditHistoryItemDto(
                row.id(), row.version(), row.actionType(), row.actorId(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.prevId(), row.snapshot(), row.prevSnapshot());
    }

    private AuditActivityItemDto<AuditableSnapshot> toActivityItem(AuditLogProjection row) {
        if (row.snapshot() == null) {
            log.warn("Audit row id={} has null snapshot, rendering with empty changes", row.id());
            return new AuditActivityItemDto<>(row.id(), row.entityId(), row.entityType(), row.actionType(), row.createdAt(),
                    List.of(), row.actorId(), null);
        }
        return new AuditActivityItemDto<>(
                row.id(), row.entityId(), row.entityType(), row.actionType(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.actorId(), row.snapshot());
    }
}
