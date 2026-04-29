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
    public List<ChangeEntry> getPhotoChanges(Long adId, int version) {
        return photoSnapshotService.getChangesForVersion(adId, version);
    }

    @Override
    public boolean photosMatchCurrent(Long adId, int version) {
        return photoSnapshotService.photosMatchCurrent(adId, version);
    }

    @Override
    public String getPhotoStateAtVersion(Long adId, int version) {
        return photoSnapshotService.getPhotoStateAtVersion(adId, version);
    }

    @Override
    public String getPhotoStateForAdvSnapshot(Long adId, Long advSnapshotId) {
        return photoSnapshotService.getPhotoStateForAdvSnapshot(adId, advSnapshotId);
    }
}
