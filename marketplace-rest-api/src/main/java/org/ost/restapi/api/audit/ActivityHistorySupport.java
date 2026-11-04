package org.ost.restapi.api.audit;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/** Shared read side of every resource's {@code /history} sub-collection: wraps {@link AuditQueryService}, which enforces who may see which entry. */
@Component
@RequiredArgsConstructor
public class ActivityHistorySupport {

    private final AuditQueryService auditQueryService;

    public List<HistoryEntryResponse> history(@NonNull Long actorId, @NonNull EntityType entityType, @NonNull Long entityId) {
        return auditQueryService.getEntityActivity(actorId, entityType, entityId).stream()
                .map(item -> new HistoryEntryResponse(item.snapshotId(), item.version(), item.actionType(),
                        item.actorId(), item.createdAt(), item.changes()))
                .toList();
    }

    public <T extends AuditableSnapshot> SnapshotResponse snapshot(@NonNull Long actorId, @NonNull EntityType entityType,
            @NonNull Long entityId, @NonNull Long snapshotId, @NonNull Class<T> snapshotClass) {
        return auditQueryService.getSnapshotContent(actorId, entityId, snapshotId, entityType, snapshotClass)
                .map(content -> new SnapshotResponse(content.snapshotData(), content.version()))
                .orElseThrow(() -> new NoSuchElementException("Snapshot " + snapshotId + " not found"));
    }

    /** One entry of an entity's history, newest first. */
    public record HistoryEntryResponse(Long snapshotId, int version, ActionType actionType, Long actorId,
                                       Instant createdAt, List<ChangeEntry> changes) {
    }

    /** A recorded state of the entity; a client restores it by sending the values back in a normal update. */
    public record SnapshotResponse(AuditableSnapshot snapshot, int version) {
    }
}
