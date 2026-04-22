package org.ost.advertisement.dto;

import org.ost.advertisement.entities.ActionType;

import java.time.Instant;

public record AdvertisementHistoryDto(
        Long       snapshotId,
        int        version,
        ActionType actionType,
        String     changedByUserName,
        Instant    createdAt,
        String     title,
        String     description,
        String     changesSummary,
        String[]   attachmentUrls,
        Long       prevSnapshotId,
        String     prevTitle,
        String     prevDescription,
        String[]   prevAttachmentUrls
) {}
