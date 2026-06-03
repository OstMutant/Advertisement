package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.ComponentFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityEnrichHookImpl implements AuditActivityEnrichHook {

    private final ComponentFactory<AttachmentAuditHook> attachmentAuditHookFactory;

    @Override
    public EntityType entityType() {
        return EntityType.ADVERTISEMENT;
    }

    @Override
    public List<AuditActivityItemDto<AuditableSnapshot>> merge(List<EntityRef> subjects, List<AuditActivityItemDto<AuditableSnapshot>> base) {
        AttachmentAuditHook hook = attachmentAuditHookFactory.getIfAvailable();
        EntityRef primary = subjects.isEmpty() ? null : subjects.getFirst();
        return hook != null ? hook.merge(primary, base) : base;
    }

    @Override
    public List<ChangeEntry> getAdditionalChanges(EntityRef entity, int version) {
        AttachmentAuditHook hook = attachmentAuditHookFactory.getIfAvailable();
        return hook != null ? hook.getMediaChanges(entity, version) : List.of();
    }

    @Override
    public boolean matchesCurrent(EntityRef entity, int version) {
        AttachmentAuditHook hook = attachmentAuditHookFactory.getIfAvailable();
        return hook == null || hook.mediaMatchCurrent(entity, version);
    }

    @Override
    public String getMediaStateForSnapshot(EntityRef ref, Long snapshotId) {
        AttachmentAuditHook hook = attachmentAuditHookFactory.getIfAvailable();
        return hook != null ? hook.getMediaStateForSnapshot(ref, snapshotId) : null;
    }

    @Override
    public String getMediaStateAtVersion(EntityRef ref, int version) {
        AttachmentAuditHook hook = attachmentAuditHookFactory.getIfAvailable();
        return hook != null ? hook.getMediaStateAtVersion(ref, version) : null;
    }
}
