package org.ost.platform.audit.spi;

import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Extension: audit-starter → attachment-starter.
 * Audit-starter calls this to let attachment-starter contribute additional
 * {@link ActivityItemDto} entries (e.g. photo uploads) into a subject's activity feed.
 * Implementation lives in attachment-spring-boot-starter.
 * Injected via {@code ObjectProvider} — no-op when attachment is disabled.
 */
public interface ActivityFeedExtension {

    List<ActivityItemDto> merge(EntityType subjectType, Long subjectId, List<ActivityItemDto> baseItems);
}
