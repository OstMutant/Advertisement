package org.ost.platform.attachment.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;

import java.util.List;

/**
 * Hook: marketplace → attachment-starter.
 * Provides attachment-domain data to marketplace orchestration code.
 * Marketplace delegates to this hook from {@code AuditActivityEnrichHookImpl}.
 * Implementation lives in attachment-spring-boot-starter.
 * Injected via {@code ObjectProvider} — no-op when attachment is absent.
 */
public interface AttachmentAuditHook {

    // ── Activity feed ──────────────────────────────────────────────────────────

    List<AuditTimelineItemDto<AuditableSnapshot>> merge(@NonNull EntityRef subject, @NonNull List<AuditTimelineItemDto<AuditableSnapshot>> baseItems);

    // ── Media history ──────────────────────────────────────────────────────────

    List<ChangeEntry> getMediaChanges(@NonNull EntityRef entity, int version);

    boolean mediaMatchCurrent(@NonNull EntityRef entity, int version);

    String getMediaStateAtVersion(@NonNull EntityRef entity, int version);

    String getMediaStateForSnapshot(@NonNull EntityRef entity, @NonNull Long snapshotId);
}
