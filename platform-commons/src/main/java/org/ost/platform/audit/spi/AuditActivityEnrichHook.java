package org.ost.platform.audit.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Hook: audit-starter → marketplace.
 * Marketplace implements this per entity type to enrich audit data with attachment-domain
 * information and to provide media-state lookups for the audit renderer.
 * Implement one bean per entity type that has attachment support.
 */
public interface AuditActivityEnrichHook {
    EntityType entityType();
    List<AuditTimelineItemDto<AuditableSnapshot>> merge(@NonNull List<EntityRef> subjects, @NonNull List<AuditTimelineItemDto<AuditableSnapshot>> base);
    List<ChangeEntry> getAdditionalChanges(@NonNull EntityRef entity, int version);
    boolean matchesCurrent(@NonNull EntityRef entity, int version);
    default String getMediaStateForSnapshot(@NonNull EntityRef ref, @NonNull Long snapshotId) { return null; }
    default String getMediaStateAtVersion(@NonNull EntityRef ref, int version) { return null; }
}
