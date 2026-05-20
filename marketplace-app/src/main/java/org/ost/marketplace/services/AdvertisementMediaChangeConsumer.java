package org.ost.marketplace.services;

import org.ost.marketplace.repository.advertisement.AdvertisementRepository;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.spi.MediaChangeConsumer;
import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementMediaChangeConsumer implements MediaChangeConsumer {

    private final AdvertisementRepository advertisementRepository;
    private final AttachmentPort          attachmentPort;

    public AdvertisementMediaChangeConsumer(AdvertisementRepository advertisementRepository,
                                            AttachmentPort attachmentPort) {
        this.advertisementRepository = advertisementRepository;
        this.attachmentPort          = attachmentPort;
    }

    @Override
    public void onMediaChanged(EntityType entityType, Long entityId) {
        if (entityType != EntityType.ADVERTISEMENT) return;
        MediaSummaryDto summary = attachmentPort.getMediaSummary(entityType, entityId);
        advertisementRepository.updateMedia(entityId, summary);
    }
}
