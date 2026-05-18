package org.ost.platform.audit.api;

import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.platform.audit.dto.UserSnapshotState;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;

public interface AuditPort {
    void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId);
    void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId);
    void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId);

    Optional<SnapshotContent>   getSnapshotContent(Long snapshotId, EntityType entityType);
    Optional<UserSnapshotState> getUserStateBefore(Long snapshotId);
    Optional<UserSnapshotState> getUserStateAt(Long snapshotId);

    void appendNoteToLastSnapshot(EntityType entityType, Long entityId, String note);
}
