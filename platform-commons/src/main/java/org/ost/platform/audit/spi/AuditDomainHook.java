package org.ost.platform.audit.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.core.model.EntityType;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Hook: audit-starter → marketplace.
 * Combines actor name resolution, entity existence checks, and display name resolution —
 * all are domain lookups that audit-starter delegates to marketplace.
 * Marketplace implements this against its own user and entity repositories.
 * Injected via {@code ObjectProvider} — gracefully absent when not registered.
 */
public interface AuditDomainHook {

    Map<Long, String> resolveNames(@NonNull Set<Long> actorIds);

    Set<Long> findExisting(@NonNull EntityType entityType, @NonNull Set<Long> entityIds);

    // snapshot may be null for entities that have been hard-deleted with no retained snapshot
    String resolveDisplayName(@NonNull EntityType entityType, AuditableSnapshot snapshot);

    <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> castIfKnown(@NonNull AuditSnapshotContentDto<? extends AuditableSnapshot> content);
}
