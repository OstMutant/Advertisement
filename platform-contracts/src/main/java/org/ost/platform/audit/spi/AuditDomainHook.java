package org.ost.platform.audit.spi;

import org.ost.platform.core.model.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * Hook: audit-starter → marketplace.
 * Combines actor name resolution and entity existence checks —
 * both are domain lookups that audit-starter delegates to marketplace.
 * Marketplace implements this against its own user and entity repositories.
 * Injected via {@code ObjectProvider} — gracefully absent when not registered.
 */
public interface AuditDomainHook {

    Map<Long, String> resolveNames(Set<Long> actorIds);

    Set<Long> findExisting(EntityType entityType, Set<Long> entityIds);
}
