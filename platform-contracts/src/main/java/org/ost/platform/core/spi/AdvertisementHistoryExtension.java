package org.ost.platform.core.spi;

import org.ost.platform.core.model.ChangeEntry;

import java.util.List;

public interface AdvertisementHistoryExtension {

    List<ChangeEntry> getMediaChanges(Long adId, int version);

    boolean mediaMatchCurrent(Long adId, int version);

    String getMediaStateAtVersion(Long adId, int version);

    String getMediaStateForAdvSnapshot(Long adId, Long advSnapshotId);
}
