package org.ost.platform.audit.spi;

import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Extension: audit-starter → attachment-starter.
 * Audit-starter calls this to include media-change entries in the entity history
 * timeline. Attachment-starter provides the snapshot-level media diff.
 * Implementation lives in attachment-spring-boot-starter.
 * Injected via {@code ObjectProvider} — no-op when attachment is disabled.
 */
public interface MediaHistoryExtension {

    List<ChangeEntry> getMediaChanges(EntityType entityType, Long entityId, int version);

    boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version);

    String getMediaStateAtVersion(EntityType entityType, Long entityId, int version);

    String getMediaStateForSnapshot(EntityType entityType, Long entityId, Long snapshotId);
}
