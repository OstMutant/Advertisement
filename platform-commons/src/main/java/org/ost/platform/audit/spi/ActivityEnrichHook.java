package org.ost.platform.audit.spi;

import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;

import java.util.List;

/**
 * Hook: audit-starter → marketplace.
 * Marketplace implements this to enrich audit data with attachment-domain information.
 * Injected via ObjectProvider — no-op when not implemented.
 */
public interface ActivityEnrichHook {
    List<AuditActivityItemDto> merge(EntityRef subject, List<AuditActivityItemDto> base);
    List<ChangeEntry> getAdditionalChanges(EntityRef entity, int version);
    boolean matchesCurrent(EntityRef entity, int version);
}
