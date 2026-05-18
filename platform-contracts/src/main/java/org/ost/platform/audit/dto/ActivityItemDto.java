package org.ost.platform.audit.dto;

import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;
import java.util.List;

public record ActivityItemDto(
        Long              snapshotId,
        Long              entityId,
        EntityType        entityType,
        String            displayName,
        ActionType        actionType,
        Instant           createdAt,
        boolean           entityExists,
        List<ChangeEntry> changes,
        Long              changedByUserId,
        String            changedByName,
        SnapshotPayload   snapshotData
) {}
