package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.attachment.service.PhotoSnapshotService;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class AdvertisementHistoryExtensionImpl implements AdvertisementHistoryExtension {

    private final PhotoSnapshotService photoSnapshotService;

    @Override
    public List<ChangeEntry> getMediaChanges(Long adId, int version) {
        return photoSnapshotService.getChangesForVersion(adId, version);
    }

    @Override
    public boolean mediaMatchCurrent(Long adId, int version) {
        return photoSnapshotService.mediaMatchCurrent(adId, version);
    }

    @Override
    public String getMediaStateAtVersion(Long adId, int version) {
        return photoSnapshotService.getMediaStateAtVersion(adId, version);
    }

    @Override
    public String getMediaStateForAdvSnapshot(Long adId, Long advSnapshotId) {
        return photoSnapshotService.getMediaStateForAdvSnapshot(adId, advSnapshotId);
    }
}
