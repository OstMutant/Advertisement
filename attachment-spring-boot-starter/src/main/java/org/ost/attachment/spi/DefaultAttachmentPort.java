package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.core.model.EntityRef;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultAttachmentPort implements AttachmentPort {

    private final AttachmentService         attachmentService;
    private final AttachmentSnapshotService attachmentSnapshotService;

    @Override
    public void softDeleteAll(EntityRef entity, Long actorId) {
        attachmentService.softDeleteAll(entity.entityType(), entity.entityId(), actorId);
    }

    @Override
    public void restoreToSnapshot(EntityRef entity, int snapshotVersion, Long actorId) {
        String[] targetUrls = attachmentSnapshotService.getUrlsAtVersion(entity.entityType(), entity.entityId(), snapshotVersion);
        attachmentService.restoreToUrls(entity.entityType(), entity.entityId(), targetUrls, actorId);
        attachmentSnapshotService.capture(entity.entityType(), entity.entityId(), actorId);
    }

    @Override
    public AttachmentMediaSummaryDto getMediaSummary(EntityRef entity) {
        return attachmentService.getMediaSummary(entity.entityType(), entity.entityId());
    }
}
