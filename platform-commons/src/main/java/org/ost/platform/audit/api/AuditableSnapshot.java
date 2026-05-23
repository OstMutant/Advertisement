package org.ost.platform.audit.api;

import org.ost.platform.core.model.EntityType;

public interface AuditableSnapshot {
    EntityType entityType();
}
