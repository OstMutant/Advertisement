package org.ost.attachment.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
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
    public List<AuditTimelineItemDto<AuditableSnapshot>> merge(@NonNull EntityRef subject, @NonNull List<AuditTimelineItemDto<AuditableSnapshot>> baseItems) {
        return attachmentSnapshotService.mergeAttachmentMediaChanges(baseItems);
    }

    // ── Media history ──────────────────────────────────────────────────────────

    @Override
    public List<ChangeEntry> getMediaChanges(@NonNull EntityRef entity, int version) {
        return attachmentSnapshotService.getChangesForVersion(entity.entityType(), entity.entityId(), version);
    }

    @Override
    public boolean mediaMatchCurrent(@NonNull EntityRef entity, int version) {
        return attachmentSnapshotService.mediaMatchCurrent(entity.entityType(), entity.entityId(), version);
    }

    @Override
    public String getMediaStateAtVersion(@NonNull EntityRef entity, int version) {
        return attachmentSnapshotService.getMediaStateAtVersion(entity.entityType(), entity.entityId(), version);
    }

    @Override
    public String getMediaStateForSnapshot(@NonNull EntityRef entity, @NonNull Long snapshotId) {
        return attachmentSnapshotService.getMediaStateForSnapshot(entity.entityType(), entity.entityId(), snapshotId);
    }
}
