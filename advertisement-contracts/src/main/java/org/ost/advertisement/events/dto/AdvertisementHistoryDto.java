package org.ost.advertisement.events.dto;

import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;

import java.time.Instant;
import java.util.List;

public record AdvertisementHistoryDto(
        Long              snapshotId,
        int               version,
        ActionType        actionType,
        String            changedByUserName,
        Instant           createdAt,
        String            title,
        String            description,
        List<ChangeEntry> changes,
        Long              prevSnapshotId,
        String            prevTitle,
        String            prevDescription
) {}
