package org.ost.advertisement.core.spi;

import org.ost.advertisement.audit.dto.SnapshotPayload;
import org.ost.advertisement.core.model.EntityType;

public interface EntityDisplayNameResolver {
    boolean supports(EntityType entityType);
    String resolveDisplayName(EntityType entityType, SnapshotPayload snapshot);
}
