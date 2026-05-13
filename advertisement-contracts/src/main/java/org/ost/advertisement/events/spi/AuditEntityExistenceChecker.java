package org.ost.advertisement.events.spi;

import org.ost.advertisement.events.model.EntityType;

import java.util.Set;

public interface AuditEntityExistenceChecker {

    Set<Long> findExisting(EntityType entityType, Set<Long> entityIds);
}
