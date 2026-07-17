package org.ost.platform.audit.dto;

import lombok.experimental.FieldNameConstants;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;

import java.time.Instant;
import java.util.List;

@FieldNameConstants
public record AuditTimelineItemDto<T extends AuditableSnapshot>(
        Long              snapshotId,
        EntityRef         entityRef,
        ActionType        actionType,
        Instant           createdAt,
        List<ChangeEntry> changes,
        Long              changedByActorId,
        T                 snapshotData,
        T                 prevSnapshotData
) {
    public AuditTimelineItemDto<T> withChanges(List<ChangeEntry> newChanges) {
        return new AuditTimelineItemDto<>(snapshotId, entityRef, actionType,
                createdAt, newChanges, changedByActorId, snapshotData, prevSnapshotData);
    }
}
