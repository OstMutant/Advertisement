package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AttachmentAuditHookImpl implements AttachmentAuditHook {

    private final AttachmentSnapshotService attachmentSnapshotService;

    // ── Activity feed ──────────────────────────────────────────────────────────

    @Override
    public List<ActivityItemDto> merge(EntityType subjectType, Long subjectId, List<ActivityItemDto> baseItems) {
        return baseItems.stream()
                .map(item -> {
                    List<ChangeEntry> mediaChanges = attachmentSnapshotService
                            .getChangesForSnapshot(item.entityType(), item.entityId(), item.snapshotId());
                    if (mediaChanges.isEmpty()) return item;
                    List<ChangeEntry> merged = new ArrayList<>(mediaChanges);
                    merged.addAll(item.changes());
                    return new ActivityItemDto(
                            item.snapshotId(), item.entityId(), item.entityType(),
                            item.displayName(), item.actionType(), item.createdAt(),
                            item.entityExists(), merged, item.changedByActorId(),
                            item.changedByName(), item.snapshotData());
                })
                .toList();
    }

    // ── Media history ──────────────────────────────────────────────────────────

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
    public String getMediaStateForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return attachmentSnapshotService.getMediaStateForSnapshot(entityType, entityId, snapshotId);
    }
}
