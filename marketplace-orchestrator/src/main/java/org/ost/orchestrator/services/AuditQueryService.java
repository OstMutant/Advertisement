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
    private final AuthorizationService authorizationService;

    public Optional<AuditableSnapshot> getLastSnapshot(@NonNull EntityType entityType, @NonNull Long entityId) {
        return auditPortFactory.findIfAvailable().flatMap(p -> p.getLastSnapshot(entityType, entityId));
    }

    /** Privileged users see every entry of the entity; everyone else only the entries they authored. */
    public List<AuditActivityItemDto<? extends AuditableSnapshot>> getEntityActivity(
            @NonNull Long actingUserId, @NonNull EntityType entityType, @NonNull Long entityId) {
        boolean showAll = authorizationService.isPrivileged(actingUserId);
        return auditPortFactory.findIfAvailable()
                .map(p -> p.getEntityActivity(entityType, entityId, actingUserId, showAll))
                .orElse(List.of());
    }

    /** A privileged user reads any snapshot; anyone else only one that appears in their own visible activity of the entity. */
    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getSnapshotContent(
            @NonNull Long actingUserId, @NonNull Long entityId, @NonNull Long snapshotId,
            @NonNull EntityType entityType, @NonNull Class<T> targetClass) {
        if (!authorizationService.isPrivileged(actingUserId)) {
            boolean visible = getEntityActivity(actingUserId, entityType, entityId).stream()
                    .anyMatch(item -> snapshotId.equals(item.snapshotId()));
            if (!visible) {
                throw new AccessDeniedException("User " + actingUserId + " may not read snapshot " + snapshotId);
            }
        }
        return auditPortFactory.findIfAvailable().flatMap(p -> p.getSnapshotContent(snapshotId, entityType, targetClass));
    }

    public List<AuditTimelineItemDto<AuditableSnapshot>> getTimelinePage(
            @NonNull Long actingUserId, @NonNull AuditTimelineFilterDto filter, @NonNull Sort sort, int page, int size) {
        authorizationService.requireIsPrivileged(actingUserId);
        return auditPortFactory.findIfAvailable()
                .map(p -> p.getTimelinePage(filter, sort, page, size))
                .orElse(List.of());
    }

    public int countTimeline(@NonNull Long actingUserId, @NonNull AuditTimelineFilterDto filter) {
        authorizationService.requireIsPrivileged(actingUserId);
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
