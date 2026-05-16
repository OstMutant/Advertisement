package org.ost.advertisement.core.spi;

import org.ost.advertisement.core.model.ChangeEntry;

import java.util.List;

public interface AdvertisementHistoryExtension {

    List<ChangeEntry> getMediaChanges(Long adId, int version);

    boolean mediaMatchCurrent(Long adId, int version);

    String getMediaStateAtVersion(Long adId, int version);

    String getMediaStateForAdvSnapshot(Long adId, Long advSnapshotId);
}
