package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.dto.SnapshotContent;
import org.ost.advertisement.audit.dto.UserSnapshotState;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditReadRepository;
import org.ost.advertisement.core.model.EntityType;
import org.ost.advertisement.core.config.UserSettings;

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
