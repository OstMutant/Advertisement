package org.ost.platform.audit.dto;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;
import java.util.List;

public record AuditActivityItemDto(
        Long              snapshotId,
        Long              entityId,
        EntityType        entityType,
        ActionType        actionType,
        Instant           createdAt,
        List<ChangeEntry> changes,
        Long              changedByActorId,
        AuditableSnapshot snapshotData
) {
    public AuditActivityItemDto withChanges(List<ChangeEntry> newChanges) {
        return new AuditActivityItemDto(snapshotId, entityId, entityType, actionType,
                createdAt, newChanges, changedByActorId, snapshotData);
    }
}
