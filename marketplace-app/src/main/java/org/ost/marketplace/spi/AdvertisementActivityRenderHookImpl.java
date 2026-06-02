package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.spi.AuditActivityRenderHook;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdvertisementActivityRenderHookImpl implements AuditActivityRenderHook {

    private final ObjectProvider<AttachmentAuditHook> attachmentAuditHook;

    @Override
    public EntityType entityType() {
        return EntityType.ADVERTISEMENT;
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
