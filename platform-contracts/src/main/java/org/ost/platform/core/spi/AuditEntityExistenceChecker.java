package org.ost.platform.core.spi;

import org.ost.platform.core.model.EntityType;

import java.util.Set;

public interface AuditEntityExistenceChecker {

    Set<Long> findExisting(EntityType entityType, Set<Long> entityIds);
}
