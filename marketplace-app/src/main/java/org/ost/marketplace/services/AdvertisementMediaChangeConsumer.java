package org.ost.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.repository.advertisement.AdvertisementRepository;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.attachment.spi.MediaChangeHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertisementMediaChangeConsumer implements MediaChangeHook {

    private final AdvertisementRepository        advertisementRepository;
    private final ObjectProvider<AttachmentPort> attachmentPort;

    @Override
    public void onMediaChanged(EntityType entityType, Long entityId) {
        if (entityType != EntityType.ADVERTISEMENT) return;
        attachmentPort.ifAvailable(port ->
                advertisementRepository.updateMedia(entityId, port.getMediaSummary(entityType, entityId))
        );
    }
}
