package org.ost.advertisement.audit.dto;

import org.ost.advertisement.core.model.ActionType;
import org.ost.advertisement.core.model.ChangeEntry;

import java.time.Instant;
import java.util.List;

public record AdvertisementHistoryDto(
        Long              snapshotId,
        int               version,
        ActionType        actionType,
        Long              actorId,
        String            changedByUserName,
        Instant           createdAt,
        String            title,
        String            description,
        List<ChangeEntry> changes,
        Long              prevSnapshotId,
        String            prevTitle,
        String            prevDescription
) {}
