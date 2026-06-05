package org.ost.platform.attachment.spi;

import lombok.NonNull;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.core.model.EntityRef;

/**
 * Domain-facing port for attachment lifecycle commands and queries.
 * Symmetric with {@code AuditPort}: domain code invokes the port directly
 * instead of publishing events. Implementation lives in attachment-starter;
 * a NoOp fallback is provided when the storage subsystem is disabled.
 */
public interface AttachmentPort {

    /** Soft-delete all attachments of an entity. Called on entity soft-delete. */
    void softDeleteAll(@NonNull EntityRef entity, @NonNull Long actorId);

    /** Restore attachments to the state captured at the given snapshot version. */
    void restoreToSnapshot(@NonNull EntityRef entity, int snapshotVersion, @NonNull Long actorId);

    /** Display-ready summary of the entity's current attachment state. */
    AttachmentMediaSummaryDto getMediaSummary(@NonNull EntityRef entity);
}
