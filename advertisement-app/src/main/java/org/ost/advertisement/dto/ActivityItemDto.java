package org.ost.advertisement.dto;

import org.ost.advertisement.entities.ActionType;

import java.time.Instant;

public record ActivityItemDto(
        Long       snapshotId,
        Long       entityId,
        String     entityType,
        String     displayName,
        ActionType actionType,
        Instant    createdAt,
        boolean    entityExists,
        String     changesSummary,
        Long       changedByUserId,
        String     changedByName
) {}
