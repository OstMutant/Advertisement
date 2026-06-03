package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityEnrichHookImpl implements AuditActivityEnrichHook {

    private final ObjectProvider<AttachmentAuditHook> attachmentAuditHook;

    @Override
    public EntityType entityType() {
        return EntityType.ADVERTISEMENT;
    }

    @Override
    public List<AuditActivityItemDto> merge(List<EntityRef> subjects, List<AuditActivityItemDto> base) {
        AttachmentAuditHook hook = attachmentAuditHook.getIfAvailable();
        EntityRef primary = subjects.isEmpty() ? null : subjects.getFirst();
        return hook != null ? hook.merge(primary, base) : base;
    }

    @Override
    public List<ChangeEntry> getAdditionalChanges(EntityRef entity, int version) {
        AttachmentAuditHook hook = attachmentAuditHook.getIfAvailable();
        return hook != null ? hook.getMediaChanges(entity, version) : List.of();
    }

    @Override
    public boolean matchesCurrent(EntityRef entity, int version) {
        AttachmentAuditHook hook = attachmentAuditHook.getIfAvailable();
        return hook == null || hook.mediaMatchCurrent(entity, version);
    }

    @Override
    public String getMediaStateForSnapshot(EntityRef ref, Long snapshotId) {
        AttachmentAuditHook hook = attachmentAuditHook.getIfAvailable();
        return hook != null ? hook.getMediaStateForSnapshot(ref, snapshotId) : null;
    }

    @Override
    public String getMediaStateAtVersion(EntityRef ref, int version) {
        AttachmentAuditHook hook = attachmentAuditHook.getIfAvailable();
        return hook != null ? hook.getMediaStateAtVersion(ref, version) : null;
    }
}
