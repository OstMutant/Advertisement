package org.ost.attachment.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttachmentAuditHookImpl implements AttachmentAuditHook {

    private final AttachmentSnapshotService attachmentSnapshotService;

    @Override
    public List<ChangeEntry> getChangesBySnapshotId(@NonNull Long attachmentSnapshotId) {
        return attachmentSnapshotService.getChangesBySnapshotId(attachmentSnapshotId);
    }

    @Override
    public String getMediaStateForSnapshot(@NonNull EntityRef entity, @NonNull Long snapshotId) {
        return attachmentSnapshotService.getMediaStateForSnapshot(entity.entityType(), entity.entityId(), snapshotId);
    }
}
