package org.ost.platform.core.spi;

import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.platform.core.model.EntityType;

public interface EntityDisplayNameResolver {
    boolean supports(EntityType entityType);
    String resolveDisplayName(EntityType entityType, SnapshotPayloadDto snapshot);
}
