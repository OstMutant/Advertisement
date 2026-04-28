package org.ost.advertisement.dto;

import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.model.ChangeEntry;

import java.time.Instant;
import java.util.List;

public record ActivityItemDto(
        Long             snapshotId,
        Long             entityId,
        String           entityType,
        String           displayName,
        ActionType       actionType,
        Instant          createdAt,
        boolean          entityExists,
        List<ChangeEntry> changes,
        Long             changedByUserId,
        String           changedByName,
        String           snapshotTitle,
        String           snapshotDescription
) {}
