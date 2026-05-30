package org.ost.platform.audit.spi;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;

/**
 * Port: marketplace → audit-starter.
 * Service facade for the audit write side. Domain code calls this port directly to
 * record entity lifecycle events and read back snapshot content.
 * Implementation: {@code DefaultAuditPort} in audit-spring-boot-starter.
 */
public interface AuditPort {
    void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId);
    void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId);
    void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId);

    Optional<AuditSnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType);
    Optional<AuditSnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType);

    default <T extends AuditableSnapshot> Optional<T> getSnapshotContent(Long snapshotId, EntityType entityType, Class<T> type) {
        return getSnapshotContent(snapshotId, entityType)
                .map(AuditSnapshotContentDto::snapshotData)
                .filter(type::isInstance)
                .map(type::cast);
    }

    default <T extends AuditableSnapshot> Optional<T> getPreviousSnapshotContent(Long snapshotId, EntityType entityType, Class<T> type) {
        return getPreviousSnapshotContent(snapshotId, entityType)
                .map(AuditSnapshotContentDto::snapshotData)
                .filter(type::isInstance)
                .map(type::cast);
    }

}
