package org.ost.advertisement.events.spi;

import java.util.Set;

public interface AuditEntityExistenceChecker {

    Set<Long> findExisting(String entityType, Set<Long> entityIds);
}
