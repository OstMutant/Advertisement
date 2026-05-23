package org.ost.platform.audit.spi;

import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Hook: audit-starter → marketplace.
 * Audit-starter calls this to expand an {@link ActivityItemDto}'s change list with
 * domain-specific derived fields (e.g. media counts, role labels).
 * Marketplace registers one hook per {@link EntityType} it supports via {@code supports()}.
 */
public interface ActivityFieldsHook {
    boolean supports(EntityType entityType);
    List<ChangeEntry> expandFields(ActivityItemDto item);
}
