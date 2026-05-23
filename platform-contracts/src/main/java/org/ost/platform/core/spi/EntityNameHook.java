package org.ost.platform.core.spi;

import org.ost.platform.audit.dto.SnapshotPayloadDto;
import org.ost.platform.core.model.EntityType;

/**
 * Hook: audit-starter → marketplace.
 * Audit-starter calls this to turn a snapshot payload into a human-readable entity name
 * (e.g. advertisement title, user full name) for history panel headings.
 * Marketplace registers one hook per {@link EntityType} it supports via {@code supports()}.
 */
public interface EntityNameHook {
    boolean supports(EntityType entityType);
    String resolveDisplayName(EntityType entityType, SnapshotPayloadDto snapshot);
}
