package org.ost.platform.audit.spi;

import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

/**
 * Hook: audit-starter → marketplace.
 * Provides media-state lookups for entity types that have attachment support,
 * letting the audit-starter render media-aware change rows without knowing
 * the domain structure. Implement one bean per entity type that has attachments.
 */
public interface AuditActivityRenderHook {

    EntityType entityType();

    String getMediaStateForSnapshot(EntityRef ref, Long snapshotId);

    String getMediaStateAtVersion(EntityRef ref, int version);
}
