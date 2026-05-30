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
        String            displayName,
        ActionType        actionType,
        Instant           createdAt,
        boolean           entityExists,
        List<ChangeEntry> changes,
        Long              changedByActorId,
        String            changedByName,
        AuditableSnapshot snapshotData
) {
    public AuditActivityItemDto withDisplayName(String name) {
        return new AuditActivityItemDto(snapshotId, entityId, entityType, name, actionType,
                createdAt, entityExists, changes, changedByActorId, changedByName, snapshotData);
    }

    public AuditActivityItemDto withChangedByName(String name) {
        return new AuditActivityItemDto(snapshotId, entityId, entityType, displayName, actionType,
                createdAt, entityExists, changes, changedByActorId, name, snapshotData);
    }

    public AuditActivityItemDto withEntityExists(boolean exists) {
        return new AuditActivityItemDto(snapshotId, entityId, entityType, displayName, actionType,
                createdAt, exists, changes, changedByActorId, changedByName, snapshotData);
    }

    public AuditActivityItemDto withChanges(List<ChangeEntry> newChanges) {
        return new AuditActivityItemDto(snapshotId, entityId, entityType, displayName, actionType,
                createdAt, entityExists, newChanges, changedByActorId, changedByName, snapshotData);
    }
}
