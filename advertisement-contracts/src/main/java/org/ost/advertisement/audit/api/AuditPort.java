package org.ost.advertisement.audit.api;

import org.ost.advertisement.audit.dto.SnapshotContent;
import org.ost.advertisement.audit.dto.UserSnapshotState;

import java.util.Optional;

public interface AuditPort {
    void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId);
    void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId);
    void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId);

    Optional<SnapshotContent>   getSnapshotContent(Long snapshotId);
    Optional<UserSnapshotState> getUserStateBefore(Long snapshotId);
    Optional<UserSnapshotState> getUserStateAt(Long snapshotId);
}
