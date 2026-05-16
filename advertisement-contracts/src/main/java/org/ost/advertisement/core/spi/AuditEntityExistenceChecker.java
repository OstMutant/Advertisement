package org.ost.advertisement.core.spi;

import org.ost.advertisement.core.model.EntityType;

import java.util.Set;

public interface AuditEntityExistenceChecker {

    Set<Long> findExisting(EntityType entityType, Set<Long> entityIds);
}
