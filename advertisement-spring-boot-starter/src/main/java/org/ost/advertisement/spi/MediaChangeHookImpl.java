package org.ost.advertisement.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.attachment.spi.AttachmentMediaChangeHook;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaChangeHookImpl implements AttachmentMediaChangeHook {

    private final AdvertisementService advertisementService;

    @Override
    public void onMediaChanged(@NonNull EntityRef entity) {
        if (entity.entityType() != EntityType.ADVERTISEMENT) return;
        advertisementService.onMediaChanged(entity.entityId());
    }
}
