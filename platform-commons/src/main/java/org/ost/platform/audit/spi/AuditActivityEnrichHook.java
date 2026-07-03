package org.ost.platform.audit.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Hook: audit-starter → marketplace.
 * Marketplace implements this per entity type to enrich audit data with attachment-domain
 * information and to provide media-state lookups for the audit renderer.
 * Implement one bean per entity type that has attachment support.
 */
public interface AuditActivityEnrichHook<T extends AuditableSnapshot> {
    EntityType entityType();
    List<AuditTimelineItemDto<T>> merge(@NonNull List<EntityRef> subjects, @NonNull List<AuditTimelineItemDto<T>> base);
    default List<AuditActivityItemDto<T>> enrichActivity(@NonNull EntityRef entityRef, @NonNull List<AuditActivityItemDto<T>> items) { return items; }
    default String getMediaStateForSnapshot(@NonNull EntityRef ref, @NonNull Long snapshotId) { return null; }
}
