package org.ost.platform.audit.spi;

import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;

import java.util.List;

/**
 * Hook: audit-starter → marketplace.
 * Audit-starter calls this to expand an {@link AuditTimelineItemDto}'s change list with
 * domain-specific derived fields and to translate raw field keys into human-readable labels.
 * Marketplace registers one bean per {@link EntityType} it supports via {@code entityType()}.
 */
public interface AuditActivityFieldsHook {
    EntityType entityType();
    List<ChangeEntry> expandFields(@NonNull AuditTimelineItemDto<AuditableSnapshot> item);
    default String labelFor(@NonNull String rawFieldKey) { return rawFieldKey; }
}
