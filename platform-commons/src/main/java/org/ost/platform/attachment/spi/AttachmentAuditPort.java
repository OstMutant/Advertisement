package org.ost.platform.attachment.spi;

import lombok.NonNull;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;

import java.util.List;

/**
 * Port: marketplace → attachment-starter.
 * Provides attachment-domain data to marketplace orchestration code.
 * Marketplace delegates to this port from {@code ActivityEnrichHookImpl}.
 * Implementation lives in attachment-spring-boot-starter.
 * Injected via {@code ObjectProvider} — no-op when attachment is absent.
 */
public interface AttachmentAuditPort {

    List<ChangeEntry> getChangesBySnapshotId(@NonNull Long attachmentSnapshotId);

    String getMediaStateForSnapshot(@NonNull EntityRef entity, @NonNull Long snapshotId);
}
