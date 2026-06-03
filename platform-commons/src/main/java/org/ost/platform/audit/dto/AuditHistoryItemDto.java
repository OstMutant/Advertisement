package org.ost.platform.audit.dto;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;

import java.time.Instant;
import java.util.List;

public record AuditHistoryItemDto(
        Long              snapshotId,
        int               version,
        ActionType        actionType,
        Long              actorId,
        Instant           createdAt,
        List<ChangeEntry> changes,
        Long              prevSnapshotId,
        AuditableSnapshot snapshotData,
        AuditableSnapshot prevSnapshotData
) {
    public AuditHistoryItemDto withChanges(List<ChangeEntry> newChanges) {
        return new AuditHistoryItemDto(snapshotId, version, actionType, actorId,
                createdAt, newChanges, prevSnapshotId, snapshotData, prevSnapshotData);
    }
}
