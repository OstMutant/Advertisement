package org.ost.advertisement.audit.services;

import org.ost.advertisement.audit.AuditPort;
import org.ost.advertisement.audit.AuditableSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoOpAuditPort implements AuditPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpAuditPort.class);

    public NoOpAuditPort() {
        log.warn("Audit subsystem disabled: NoOpAuditPort is active");
    }

    @Override
    public void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId) {
    }

    @Override
    public void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId) {
    }

    @Override
    public void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId) {
    }
}
