package org.ost.platform.audit.dto;

import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;

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
        SnapshotPayloadDto   snapshotData,
        SnapshotPayloadDto   prevSnapshotData
) {}
