package org.ost.audit.repository;

import org.ost.audit.entities.AuditLog;
import org.springframework.data.repository.CrudRepository;

interface AuditLogCrudRepository extends CrudRepository<AuditLog, Long> {
}
