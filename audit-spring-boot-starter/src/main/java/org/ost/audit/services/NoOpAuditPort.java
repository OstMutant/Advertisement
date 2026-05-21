package org.ost.audit.services;

import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.platform.core.model.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class NoOpAuditPort implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpAuditPort.class);

    public NoOpAuditPort() {
        log.warn("Audit subsystem disabled: NoOpAuditPort is active");
    }

    @Override
    public void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId) { /* audit disabled */ }

    @Override
    public void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId) { /* audit disabled */ }

    @Override
    public void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId) { /* audit disabled */ }

    @Override
    public Optional<SnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return Optional.empty();
    }

    @Override
    public Optional<SnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return Optional.empty();
    }

    @Override
    public void appendNoteToLastSnapshot(EntityType entityType, Long entityId, String note) { /* audit disabled */ }
}
