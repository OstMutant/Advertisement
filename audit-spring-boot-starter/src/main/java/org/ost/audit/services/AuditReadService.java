package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.audit.repository.AuditLogProjection;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditReadService {

    private final AuditLogRepository            repository;
    private final List<AuditActivityEnrichHook> activityEnrichHooks;

    // ── History ───────────────────────────────────────────────────────────────

    public List<AuditActivityItemDto<? extends AuditableSnapshot>> getEntityActivity(EntityType entityType, Long entityId,
                                                      Long currentUserId, boolean showAll) {
        EntityRef ref = new EntityRef(entityType, entityId);
        List<AuditLogProjection> rows = repository.findRows(entityType, entityId, showAll ? null : currentUserId, 100);
        List<AuditActivityItemDto<? extends AuditableSnapshot>> result = new ArrayList<>(rows.size());
        for (AuditLogProjection row : rows) {
            result.add(enrichWithMedia(toActivityItem(row), ref));
        }
        return result;
    }

    private <T extends AuditableSnapshot> AuditActivityItemDto<T> enrichWithMedia(AuditActivityItemDto<T> h, EntityRef ref) {
        List<ChangeEntry> mediaChanges = activityEnrichHooks.stream()
                .filter(hook -> hook.entityType() == ref.entityType())
                .findFirst()
                .map(hook -> hook.getAdditionalChanges(ref, h.version()))
                .orElse(List.of());
        if (mediaChanges.isEmpty()) return h;
        List<ChangeEntry> combined = new ArrayList<>(mediaChanges);
        combined.addAll(h.changes());
        return h.withChanges(combined);
    }

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return repository.getLastSnapshot(entityType, entityId);
    }

    // ── Activity ──────────────────────────────────────────────────────────────

    public List<AuditTimelineItemDto<AuditableSnapshot>> getTimeline(Long actorId, int limit) {
        List<AuditTimelineItemDto<AuditableSnapshot>> items = repository.findByActor(actorId, limit)
                .stream().map(this::toTimelineItem).toList();
        List<EntityRef> noSubjects = List.of();
        for (AuditActivityEnrichHook hook : activityEnrichHooks) {
            items = hook.merge(noSubjects, items);
        }
        return items;
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private AuditActivityItemDto<? extends AuditableSnapshot> toActivityItem(AuditLogProjection row) {
        if (row.snapshot() == null) {
            warnNullSnapshot(row);
            return new AuditActivityItemDto<>(row.id(), row.version(), row.actionType(), row.actorId(), row.createdAt(),
                    List.of(), row.prevId(), null, row.prevSnapshot());
        }
        return new AuditActivityItemDto<>(
                row.id(), row.version(), row.actionType(), row.actorId(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.prevId(), row.snapshot(), row.prevSnapshot());
    }

    private AuditTimelineItemDto<AuditableSnapshot> toTimelineItem(AuditLogProjection row) {
        EntityRef ref = new EntityRef(row.entityType(), row.entityId());
        if (row.snapshot() == null) {
            warnNullSnapshot(row);
            return new AuditTimelineItemDto<>(row.id(), ref, row.actionType(), row.createdAt(),
                    List.of(), row.actorId(), null);
        }
        return new AuditTimelineItemDto<>(
                row.id(), ref, row.actionType(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.actorId(), row.snapshot());
    }

    private void warnNullSnapshot(AuditLogProjection row) {
        log.warn("Audit row id={} has null snapshot, rendering with empty changes", row.id());
    }
}
