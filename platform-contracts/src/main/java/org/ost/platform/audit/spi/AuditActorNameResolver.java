package org.ost.platform.audit.spi;

import java.util.Map;
import java.util.Set;

/**
 * Resolver: audit-starter → marketplace.
 * Audit-starter calls this to resolve actor IDs to display names for history panels.
 * Marketplace implements it against its own user domain.
 * Injected via {@code ObjectProvider} — actor IDs are shown as raw numbers when absent.
 */
public interface AuditActorNameResolver {

    Map<Long, String> resolveNames(Set<Long> actorIds);
}
