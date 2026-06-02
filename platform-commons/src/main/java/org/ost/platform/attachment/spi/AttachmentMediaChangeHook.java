package org.ost.platform.attachment.spi;

import org.ost.platform.core.model.EntityRef;

/**
 * Hook: attachment-starter → marketplace.
 * Attachment-starter notifies the domain when an entity's media set changes.
 * Implementations live in the domain (e.g. marketplace-app).
 * Optional — attachment-starter injects via {@code ObjectProvider} and does
 * nothing when no implementation is present.
 *
 * <p>The contract intentionally carries only {@code (entityType, entityId)};
 * the consumer queries current state via {@code AttachmentService} to avoid
 * leaky derived fields (URLs, counts) in the SPI surface.</p>
 */
public interface AttachmentMediaChangeHook {

    void onMediaChanged(EntityRef entity);
}
