package org.ost.platform.audit.spi;

import lombok.NonNull;
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
    void captureCreation(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId);
    void captureUpdate(@NonNull Long entityId, @NonNull AuditableSnapshot before, @NonNull AuditableSnapshot after, @NonNull Long actorId);
    void captureDeletion(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId);

    <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getSnapshotContent(@NonNull Long snapshotId, @NonNull EntityType entityType);
}
