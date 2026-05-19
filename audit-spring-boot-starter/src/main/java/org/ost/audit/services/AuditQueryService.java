package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;

@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditLogRepository.getSnapshotContent(snapshotId, entityType);
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditLogRepository.getPreviousSnapshotContent(snapshotId, entityType);
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return auditLogRepository.findLastSnapshotId(entityType, entityId);
    }
}
