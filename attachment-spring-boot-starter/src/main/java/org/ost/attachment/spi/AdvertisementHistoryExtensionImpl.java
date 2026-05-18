package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.AdvertisementHistoryExtension;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class AdvertisementHistoryExtensionImpl implements AdvertisementHistoryExtension {

    private final AttachmentSnapshotService attachmentSnapshotService;

    @Override
    public List<ChangeEntry> getMediaChanges(EntityType entityType, Long entityId, int version) {
        return attachmentSnapshotService.getChangesForVersion(entityType, entityId, version);
    }

    @Override
    public boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version) {
        return attachmentSnapshotService.mediaMatchCurrent(entityType, entityId, version);
    }

    @Override
    public String getMediaStateAtVersion(EntityType entityType, Long entityId, int version) {
        return attachmentSnapshotService.getMediaStateAtVersion(entityType, entityId, version);
    }

    @Override
    public String getMediaStateForAdvSnapshot(EntityType entityType, Long entityId, Long advSnapshotId) {
        return attachmentSnapshotService.getMediaStateForAdvSnapshot(entityType, entityId, advSnapshotId);
    }
}
