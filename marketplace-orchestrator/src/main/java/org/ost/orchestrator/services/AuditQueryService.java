package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Shared read-only {@link AuditPort} lookups, reused by every marketplace-app adapter. */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final ComponentFactory<AuditPort> auditPortFactory;
    private final List<AuditActivityEnrichHook<?>> enrichHookList;

    public Optional<AuditableSnapshot> getLastSnapshot(@NonNull EntityType entityType, @NonNull Long entityId) {
        return auditPortFactory.findIfAvailable().flatMap(p -> p.getLastSnapshot(entityType, entityId));
    }

    public List<AuditActivityItemDto<? extends AuditableSnapshot>> getEntityActivity(
            @NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long userId, boolean showAll) {
        return auditPortFactory.findIfAvailable()
                .map(p -> p.getEntityActivity(entityType, entityId, userId, showAll))
                .orElse(List.of());
    }

    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getSnapshotContent(
            @NonNull Long snapshotId, @NonNull EntityType entityType, @NonNull Class<T> targetClass) {
        return auditPortFactory.findIfAvailable().flatMap(p -> p.getSnapshotContent(snapshotId, entityType, targetClass));
    }

    public List<AuditTimelineItemDto<AuditableSnapshot>> getTimelinePage(
            @NonNull AuditTimelineFilterDto filter, @NonNull Sort sort, int page, int size) {
        return auditPortFactory.findIfAvailable()
                .map(p -> p.getTimelinePage(filter, sort, page, size))
                .orElse(List.of());
    }

    public int countTimeline(@NonNull AuditTimelineFilterDto filter) {
        return auditPortFactory.findIfAvailable().map(p -> p.countTimeline(filter)).orElse(0);
    }

    public boolean isAvailable() {
        return auditPortFactory.findIfAvailable().isPresent();
    }

    public boolean hasEnrichHook(@NonNull EntityType entityType) {
        return enrichHookList.stream().anyMatch(h -> h.entityType() == entityType);
    }

    public String getMediaStateForSnapshot(@NonNull EntityRef ref, @NonNull Long attachmentSnapshotId) {
        return enrichHookList.stream()
                .filter(h -> h.entityType() == ref.entityType())
                .findFirst()
                .map(h -> h.getMediaStateForSnapshot(ref, attachmentSnapshotId))
                .orElse(null);
    }
}
