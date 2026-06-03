package org.ost.platform.attachment.spi;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
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

    List<AuditActivityItemDto<AuditableSnapshot>> merge(EntityRef subject, List<AuditActivityItemDto<AuditableSnapshot>> baseItems);

    // ── Media history ──────────────────────────────────────────────────────────

    List<ChangeEntry> getMediaChanges(EntityRef entity, int version);

    boolean mediaMatchCurrent(EntityRef entity, int version);

    String getMediaStateAtVersion(EntityRef entity, int version);

    String getMediaStateForSnapshot(EntityRef entity, Long snapshotId);
}
