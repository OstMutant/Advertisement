package org.ost.orchestrator.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AdvertisementAuditEnrichService;
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

    private final AdvertisementAuditEnrichService advertisementAuditEnrichService;

    @Override
    public EntityType entityType() {
        return EntityType.ADVERTISEMENT;
    }

    @Override
    public List<AuditTimelineItemDto<AdvertisementSnapshotDto>> merge(
            @NonNull List<AuditTimelineItemDto<AdvertisementSnapshotDto>> base) {
        return advertisementAuditEnrichService.mergeMediaChanges(base);
    }

    @Override
    public List<AuditActivityItemDto<AdvertisementSnapshotDto>> enrichActivity(
            @NonNull List<AuditActivityItemDto<AdvertisementSnapshotDto>> items) {
        return advertisementAuditEnrichService.enrichActivityItems(items);
    }

    @Override
    public String getMediaStateForSnapshot(@NonNull EntityRef ref, @NonNull Long snapshotId) {
        return advertisementAuditEnrichService.getMediaStateForSnapshot(ref, snapshotId);
    }
}
