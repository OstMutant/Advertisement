package org.ost.marketplace.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.advertisement.AdvertisementEnrichService;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityEnrichHookImpl implements AuditActivityEnrichHook<AdvertisementSnapshotDto> {

    private final AdvertisementEnrichService advertisementEnrichService;

    @Override
    public EntityType entityType() {
        return EntityType.ADVERTISEMENT;
    }

    @Override
    public List<AuditTimelineItemDto<AdvertisementSnapshotDto>> merge(
            @NonNull List<EntityRef> subjects,
            @NonNull List<AuditTimelineItemDto<AdvertisementSnapshotDto>> base) {
        return advertisementEnrichService.mergeMediaChanges(base);
    }

    @Override
    public List<AuditActivityItemDto<AdvertisementSnapshotDto>> enrichActivity(
            @NonNull EntityRef entityRef,
            @NonNull List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {
        return advertisementEnrichService.enrichActivityItems(items);
    }

    @Override
    public String getMediaStateForSnapshot(@NonNull EntityRef ref, @NonNull Long snapshotId) {
        return advertisementEnrichService.getMediaStateForSnapshot(ref, snapshotId);
    }
}
