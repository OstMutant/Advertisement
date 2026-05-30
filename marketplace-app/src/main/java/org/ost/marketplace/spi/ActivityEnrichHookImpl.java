package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.spi.ActivityEnrichHook;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ActivityEnrichHookImpl implements ActivityEnrichHook {

    private final ObjectProvider<AttachmentAuditHook> attachmentAuditHook;

    @Override
    public List<ActivityItemDto> merge(EntityRef subject, List<ActivityItemDto> base) {
        AttachmentAuditHook hook = attachmentAuditHook.getIfAvailable();
        return hook != null ? hook.merge(subject, base) : base;
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
}
