package org.ost.platform.attachment.spi;

import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Hook: audit-starter → attachment-starter.
 * Combines activity feed contribution and media history access —
 * both are attachment-domain callbacks invoked by the audit-starter.
 * Implementation lives in attachment-spring-boot-starter.
 * Injected via {@code ObjectProvider} — no-op when attachment is disabled.
 */
public interface AttachmentAuditHook {

    // ── Activity feed ──────────────────────────────────────────────────────────

    List<ActivityItemDto> merge(EntityType subjectType, Long subjectId, List<ActivityItemDto> baseItems);

    // ── Media history ──────────────────────────────────────────────────────────

    List<ChangeEntry> getMediaChanges(EntityType entityType, Long entityId, int version);

    boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version);

    String getMediaStateAtVersion(EntityType entityType, Long entityId, int version);

    String getMediaStateForSnapshot(EntityType entityType, Long entityId, Long snapshotId);
}
