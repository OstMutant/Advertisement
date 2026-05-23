package org.ost.platform.attachment.spi;

import org.ost.platform.attachment.dto.MediaSummaryDto;
import org.ost.platform.core.model.EntityType;

/**
 * Domain-facing port for attachment lifecycle commands and queries.
 * Symmetric with {@code AuditPort}: domain code invokes the port directly
 * instead of publishing events. Implementation lives in attachment-starter;
 * a NoOp fallback is provided when the storage subsystem is disabled.
 */
public interface AttachmentPort {

    /** Soft-delete all attachments of an entity. Called on entity soft-delete. */
    void softDeleteAll(EntityType entityType, Long entityId, Long actorId);

    /** Restore attachments to the state captured at the given snapshot version. */
    void restoreToSnapshot(EntityType entityType, Long entityId, int snapshotVersion, Long actorId);

    /** Display-ready summary of the entity's current attachment state. */
    MediaSummaryDto getMediaSummary(EntityType entityType, Long entityId);
}
