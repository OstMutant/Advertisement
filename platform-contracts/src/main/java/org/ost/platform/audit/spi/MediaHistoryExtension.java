package org.ost.platform.audit.spi;

import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;

public interface MediaHistoryExtension {

    List<ChangeEntry> getMediaChanges(EntityType entityType, Long entityId, int version);

    boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version);

    String getMediaStateAtVersion(EntityType entityType, Long entityId, int version);

    String getMediaStateForSnapshot(EntityType entityType, Long entityId, Long snapshotId);
}
