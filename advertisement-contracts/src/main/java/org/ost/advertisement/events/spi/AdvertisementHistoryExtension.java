package org.ost.advertisement.events.spi;

import org.ost.advertisement.events.model.ChangeEntry;

import java.util.List;

public interface AdvertisementHistoryExtension {

    List<ChangeEntry> getPhotoChanges(Long adId, int version);

    boolean photosMatchCurrent(Long adId, int version);

    String getPhotoStateAtVersion(Long adId, int version);

    String getPhotoStateForAdvSnapshot(Long adId, Long advSnapshotId);
}
