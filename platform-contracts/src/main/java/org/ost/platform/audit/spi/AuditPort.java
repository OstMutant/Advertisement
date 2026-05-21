package org.ost.platform.audit.spi;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;

public interface AuditPort {
    void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId);
    void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId);
    void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId);

    Optional<SnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType);
    Optional<SnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType);

    void appendNoteToLastSnapshot(EntityType entityType, Long entityId, String note);
}
