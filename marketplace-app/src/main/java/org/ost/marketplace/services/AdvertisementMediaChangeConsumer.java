package org.ost.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.repository.advertisement.AdvertisementDescriptor;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.spi.MediaChangeConsumer;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertisementMediaChangeConsumer implements MediaChangeConsumer {

    private final JdbcClient     jdbcClient;
    private final AttachmentPort attachmentPort;

    @Override
    public void onMediaChanged(EntityType entityType, Long entityId) {
        if (entityType != EntityType.ADVERTISEMENT) return;
        MediaSummaryDto summary = attachmentPort.getMediaSummary(entityType, entityId);
        AdvertisementDescriptor.Write.UPDATE_MEDIA.execute(jdbcClient,
                AdvertisementDescriptor.Write.updateMediaParams(entityId, summary));
    }
}
