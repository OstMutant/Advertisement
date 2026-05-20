package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.audit.repository.AuditReadRepository;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;

@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditReadRepository auditReadRepository;

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditReadRepository.getSnapshotContent(snapshotId, entityType);
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditReadRepository.getPreviousSnapshotContent(snapshotId, entityType);
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return auditReadRepository.findLastSnapshotId(entityType, entityId);
    }
}
