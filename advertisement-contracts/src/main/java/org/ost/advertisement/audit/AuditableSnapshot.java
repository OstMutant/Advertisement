package org.ost.advertisement.audit;

import org.ost.advertisement.events.model.EntityType;

public interface AuditableSnapshot {
    EntityType entityType();
}
