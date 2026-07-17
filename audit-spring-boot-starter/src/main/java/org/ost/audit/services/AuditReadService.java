package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.audit.repository.AuditLogProjection;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditReadService {

    private final AuditLogRepository                   repository;
    @SuppressWarnings("rawtypes")
    private final List<AuditActivityEnrichHook>        activityEnrichHooks;

    // ── History ───────────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AuditActivityItemDto<? extends AuditableSnapshot>> getEntityActivity(EntityType entityType, Long entityId,
                                                      Long currentUserId, boolean showAll) {
        List<AuditLogProjection> rows = withSameTypePrevSnapshot(
                repository.findRows(entityType, entityId, showAll ? null : currentUserId, 100));
        List items = rows.stream().map(this::toActivityItem).toList();
        EntityRef entityRef = new EntityRef(entityType, entityId);
        for (AuditActivityEnrichHook hook : activityEnrichHooks) {
            if (hook.entityType() == entityType) {
                items = hook.enrichActivity(entityRef, items);
            }
        }
        return items;
    }

    public Optional<AuditableSnapshot> getLastSnapshot(EntityType entityType, Long entityId) {
        return repository.getLastSnapshot(entityType, entityId);
    }

    // ── Activity ──────────────────────────────────────────────────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AuditTimelineItemDto<AuditableSnapshot>> getTimelinePage(AuditTimelineFilterDto filter, Sort sort, int page, int size) {
        List items = repository.findTimeline(filter, sort, page, size).stream().map(this::toTimelineItem).toList();
        List<EntityRef> noSubjects = List.of();
        for (AuditActivityEnrichHook hook : activityEnrichHooks) {
            items = hook.merge(noSubjects, items);
        }
        return items;
    }

    public int countTimeline(AuditTimelineFilterDto filter) {
        return repository.countTimeline(filter);
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
                    List.of(), row.actorId(), null, row.prevSnapshot());
        }
        return new AuditTimelineItemDto<>(
                row.id(), ref, row.actionType(), row.createdAt(),
                row.snapshot().diff(row.prevSnapshot()), row.actorId(), row.snapshot(), row.prevSnapshot());
    }

    private List<AuditLogProjection> withSameTypePrevSnapshot(List<AuditLogProjection> rows) {
        Map<Class<?>, AuditableSnapshot> lastByType = new HashMap<>();
        List<AuditLogProjection> result = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            AuditLogProjection row = rows.get(i);
            AuditableSnapshot snap = row.snapshot();
            AuditableSnapshot prevSameType = snap != null ? lastByType.get(snap.getClass()) : null;
            if (snap != null) lastByType.put(snap.getClass(), snap);
            result.add(0, new AuditLogProjection(row.id(), row.entityType(), row.entityId(), row.actionType(),
                    snap, row.actorId(), row.createdAt(), row.version(), row.prevId(), prevSameType));
        }
        return result;
    }

    private void warnNullSnapshot(AuditLogProjection row) {
        log.warn("Audit row id={} has null snapshot, rendering with empty changes", row.id());
    }
}
