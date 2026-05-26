package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.platform.attachment.spi.MediaChangeHook;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaChangeHookImpl implements MediaChangeHook {

    private final AdvertisementService advertisementService;

    @Override
    public void onMediaChanged(EntityRef entity) {
        if (entity.entityType() != EntityType.ADVERTISEMENT) return;
        advertisementService.onMediaChanged(entity.entityId());
    }
}
