package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.core.model.ChangeEntry;
import org.ost.advertisement.core.spi.AdvertisementHistoryExtension;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.advertisement.attachment.storage.ConditionalOnStorageEnabled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class AdvertisementHistoryExtensionImpl implements AdvertisementHistoryExtension {

    private final AttachmentSnapshotService attachmentSnapshotService;

    @Override
    public List<ChangeEntry> getMediaChanges(Long adId, int version) {
        return attachmentSnapshotService.getChangesForVersion(adId, version);
    }

    @Override
    public boolean mediaMatchCurrent(Long adId, int version) {
        return attachmentSnapshotService.mediaMatchCurrent(adId, version);
    }

    @Override
    public String getMediaStateAtVersion(Long adId, int version) {
        return attachmentSnapshotService.getMediaStateAtVersion(adId, version);
    }

    @Override
    public String getMediaStateForAdvSnapshot(Long adId, Long advSnapshotId) {
        return attachmentSnapshotService.getMediaStateForAdvSnapshot(adId, advSnapshotId);
    }
}
