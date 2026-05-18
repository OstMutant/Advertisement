package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.platform.audit.dto.UserSnapshotState;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.AuditReadRepository;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.config.UserSettings;

import java.util.Optional;

@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditReadRepository  auditReadRepository;
    private final AuditSnapshotMapper  mapper;

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditReadRepository.getSnapshotContent(snapshotId, entityType);
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return auditReadRepository.getUserStateAt(snapshotId);
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return auditReadRepository.getUserStateBefore(snapshotId);
    }

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return auditReadRepository.getSnapshotData(snapshotId, EntityType.USER_SETTINGS)
                .map(json -> mapper.fromJson(json, UserSettings.class));
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return auditReadRepository.findLastSnapshotId(entityType, entityId);
    }
}
