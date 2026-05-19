package org.ost.attachment.service;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.spi.MediaSummary;
import org.ost.platform.core.model.EntityType;

@RequiredArgsConstructor
public class DefaultAttachmentPort implements AttachmentPort {

    private final AttachmentService         attachmentService;
    private final AttachmentSnapshotService attachmentSnapshotService;

    @Override
    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        attachmentService.softDeleteAll(entityType, entityId, actorId);
    }

    @Override
    public void restoreToSnapshot(EntityType entityType, Long entityId, int snapshotVersion, Long actorId) {
        String[] targetUrls = attachmentSnapshotService.getUrlsAtVersion(entityType, entityId, snapshotVersion);
        attachmentService.restoreToUrls(entityType, entityId, targetUrls, actorId);
        attachmentSnapshotService.capture(entityType, entityId, actorId);
    }

    @Override
    public MediaSummary getMediaSummary(EntityType entityType, Long entityId) {
        return attachmentService.getMediaSummary(entityType, entityId);
    }
}
