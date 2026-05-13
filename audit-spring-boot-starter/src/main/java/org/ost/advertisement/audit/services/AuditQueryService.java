package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditLogDescriptor;
import org.ost.advertisement.audit.repository.AuditLogRepository.SnapshotContent;
import org.ost.advertisement.audit.repository.AuditReadRepository;
import org.ost.advertisement.audit.repository.AuditReadRepository.UserSnapshotState;
import org.ost.advertisement.dto.UserSettings;

import java.util.Optional;

@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditReadRepository  auditReadRepository;
    private final AuditSnapshotMapper  mapper;

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return auditReadRepository.getSnapshotContent(snapshotId);
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return auditReadRepository.getUserStateAt(snapshotId);
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return auditReadRepository.getUserStateBefore(snapshotId);
    }

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return auditReadRepository.getSnapshotData(snapshotId, AuditLogDescriptor.EntityType.USER_SETTINGS)
                .map(json -> mapper.fromJson(json, UserSettings.class));
    }

    public Optional<Long> findLastSnapshotId(Long advertisementId) {
        return auditReadRepository.findLastSnapshotId(advertisementId);
    }
}
