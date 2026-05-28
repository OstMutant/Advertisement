package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.model.EntityType;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;

    public Optional<SnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditLogRepository.getSnapshotContent(snapshotId, entityType);
    }

    public Optional<SnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditLogRepository.getPreviousSnapshotContent(snapshotId, entityType);
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return auditLogRepository.findLastSnapshotId(entityType, entityId);
    }
}
