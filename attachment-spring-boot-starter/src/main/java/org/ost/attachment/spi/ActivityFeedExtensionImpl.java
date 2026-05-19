package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityFeedExtension;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class ActivityFeedExtensionImpl implements ActivityFeedExtension {

    private final AttachmentSnapshotService attachmentSnapshotService;

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
}
