package org.ost.advertisement.audit.dto;

import org.ost.advertisement.core.model.ActionType;
import org.ost.advertisement.core.model.ChangeEntry;
import org.ost.advertisement.core.model.EntityType;

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
