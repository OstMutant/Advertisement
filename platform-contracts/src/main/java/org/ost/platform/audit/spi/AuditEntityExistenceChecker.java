package org.ost.platform.audit.spi;

import org.ost.platform.core.model.EntityType;

import java.util.Set;

/**
 * Checker: audit-starter → marketplace.
 * Audit-starter calls this to skip history entries for entities that no longer exist
 * (hard-deleted or purged). Marketplace implements it against its own repositories.
 */
public interface AuditEntityExistenceChecker {

    Set<Long> findExisting(EntityType entityType, Set<Long> entityIds);
}
