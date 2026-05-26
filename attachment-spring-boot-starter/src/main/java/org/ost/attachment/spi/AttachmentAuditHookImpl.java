package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AttachmentAuditHookImpl implements AttachmentAuditHook {

    private final AttachmentSnapshotService attachmentSnapshotService;

    // ── Activity feed ──────────────────────────────────────────────────────────

    @Override
    public List<ActivityItemDto> merge(EntityRef subject, List<ActivityItemDto> baseItems) {
        return attachmentSnapshotService.mergeMediaChanges(baseItems);
    }

    // ── Media history ──────────────────────────────────────────────────────────

    @Override
    public List<ChangeEntry> getMediaChanges(EntityRef entity, int version) {
        return attachmentSnapshotService.getChangesForVersion(entity.entityType(), entity.entityId(), version);
    }

    @Override
    public boolean mediaMatchCurrent(EntityRef entity, int version) {
        return attachmentSnapshotService.mediaMatchCurrent(entity.entityType(), entity.entityId(), version);
    }

    @Override
    public String getMediaStateAtVersion(EntityRef entity, int version) {
        return attachmentSnapshotService.getMediaStateAtVersion(entity.entityType(), entity.entityId(), version);
    }

    @Override
    public String getMediaStateForSnapshot(EntityRef entity, Long snapshotId) {
        return attachmentSnapshotService.getMediaStateForSnapshot(entity.entityType(), entity.entityId(), snapshotId);
    }
}
