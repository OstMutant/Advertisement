package org.ost.platform.taxon.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;

/**
 * Hook: taxon-starter → marketplace.
 * Called when a taxon is assigned to or unassigned from an entity.
 * Marketplace implements this to write assignment events into the advertisement activity feed.
 */
public interface TaxonAuditHook {

    void onAssignmentChanged(@NonNull EntityType entityType, @NonNull Long entityId,
                             @NonNull Long taxonId, @NonNull AssignmentChange change);

    enum AssignmentChange { ASSIGNED, UNASSIGNED }
}
