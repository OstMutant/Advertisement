package org.ost.platform.audit.spi;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
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
    List<AuditActivityItemDto<AuditableSnapshot>> merge(List<EntityRef> subjects, List<AuditActivityItemDto<AuditableSnapshot>> base);
    List<ChangeEntry> getAdditionalChanges(EntityRef entity, int version);
    boolean matchesCurrent(EntityRef entity, int version);
    default String getMediaStateForSnapshot(EntityRef ref, Long snapshotId) { return null; }
    default String getMediaStateAtVersion(EntityRef ref, int version) { return null; }
}
