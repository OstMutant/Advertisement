package org.ost.attachment.service;

import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.platform.core.model.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpAttachmentPort implements AttachmentPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpAttachmentPort.class);

    public NoOpAttachmentPort() {
        log.warn("Attachment storage disabled: NoOpAttachmentPort is active");
    }

    @Override
    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) { /* storage disabled */ }

    @Override
    public void restoreToSnapshot(EntityType entityType, Long entityId, int snapshotVersion, Long actorId) { /* storage disabled */ }

    @Override
    public MediaSummaryDto getMediaSummary(EntityType entityType, Long entityId) {
        return MediaSummaryDto.empty();
    }
}
