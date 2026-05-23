package org.ost.marketplace.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.MediaChangeHook;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaChangeHookImpl implements MediaChangeHook {

    private final AdvertisementService advertisementService;

    @Override
    public void onMediaChanged(EntityType entityType, Long entityId) {
        if (entityType != EntityType.ADVERTISEMENT) return;
        advertisementService.onMediaChanged(entityId);
    }
}
