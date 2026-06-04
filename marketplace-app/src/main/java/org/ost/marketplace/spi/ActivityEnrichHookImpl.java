package org.ost.marketplace.spi;

import lombok.NonNull;
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
    public List<AuditActivityItemDto<AuditableSnapshot>> merge(@NonNull List<EntityRef> subjects, @NonNull List<AuditActivityItemDto<AuditableSnapshot>> base) {
        EntityRef primary = subjects.isEmpty() ? null : subjects.getFirst();
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.merge(primary, base))
                .orElse(base);
    }

    @Override
    public List<ChangeEntry> getAdditionalChanges(@NonNull EntityRef entity, int version) {
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getMediaChanges(entity, version))
                .orElse(List.of());
    }

    @Override
    public boolean matchesCurrent(@NonNull EntityRef entity, int version) {
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.mediaMatchCurrent(entity, version))
                .orElse(true);
    }

    @Override
    public String getMediaStateForSnapshot(@NonNull EntityRef ref, @NonNull Long snapshotId) {
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getMediaStateForSnapshot(ref, snapshotId))
                .orElse(null);
    }

    @Override
    public String getMediaStateAtVersion(@NonNull EntityRef ref, int version) {
        return attachmentAuditHookFactory.findIfAvailable()
                .map(h -> h.getMediaStateAtVersion(ref, version))
                .orElse(null);
    }
}
