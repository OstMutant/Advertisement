package org.ost.advertisement.audit.dto;

import org.ost.advertisement.core.model.ActionType;
import org.ost.advertisement.core.model.ChangeEntry;

import java.time.Instant;
import java.util.List;

public record EntityHistoryDto(
        Long              snapshotId,
        int               version,
        ActionType        actionType,
        Long              actorId,
        String            changedByUserName,
        Instant           createdAt,
        List<ChangeEntry> changes,
        Long              prevSnapshotId,
        SnapshotPayload   snapshotData,
        SnapshotPayload   prevSnapshotData
) {}
