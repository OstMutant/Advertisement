package org.ost.advertisement.dto;

import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.model.ChangeEntry;

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
        String            prevDescription,
        String[]          prevAttachmentUrls,
        String[]          currAttachmentUrls
) {}
