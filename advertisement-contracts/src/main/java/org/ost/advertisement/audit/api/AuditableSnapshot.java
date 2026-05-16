package org.ost.advertisement.audit.api;

import org.ost.advertisement.core.model.EntityType;

public interface AuditableSnapshot {
    EntityType entityType();
}
