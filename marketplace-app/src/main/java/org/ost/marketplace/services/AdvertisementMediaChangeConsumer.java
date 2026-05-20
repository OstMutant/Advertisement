package org.ost.marketplace.services;

import org.ost.marketplace.repository.advertisement.AdvertisementDescriptor;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.spi.MediaChangeConsumer;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementMediaChangeConsumer implements MediaChangeConsumer {

    private final RepositoryCustom repo;
    private final AttachmentPort         attachmentPort;

    public AdvertisementMediaChangeConsumer(JdbcClient jdbcClient, AttachmentPort attachmentPort) {
        this.repo          = new RepositoryCustom(jdbcClient);
        this.attachmentPort = attachmentPort;
    }

    @Override
    public void onMediaChanged(EntityType entityType, Long entityId) {
        if (entityType != EntityType.ADVERTISEMENT) return;
        MediaSummaryDto summary = attachmentPort.getMediaSummary(entityType, entityId);
        repo.executeUpdate(AdvertisementDescriptor.Write.UPDATE_MEDIA,
                AdvertisementDescriptor.Write.updateMediaParams(entityId, summary));
    }
}
