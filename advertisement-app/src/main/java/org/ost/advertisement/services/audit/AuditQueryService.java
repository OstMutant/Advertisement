package org.ost.advertisement.services.audit;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditLogDescriptor;
import org.ost.advertisement.audit.repository.AuditLogRepository.SnapshotContent;
import org.ost.advertisement.dto.UserSettings;
import org.ost.advertisement.repository.audit.AuditLogRepository;
import org.ost.advertisement.repository.audit.AuditLogRepository.UserSnapshotState;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository  auditLogRepository;
    private final AuditSnapshotMapper mapper;

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return auditLogRepository.getSnapshotContent(snapshotId);
    }

    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return auditLogRepository.getUserStateAt(snapshotId);
    }

    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return auditLogRepository.getUserStateBefore(snapshotId);
    }

    public Optional<UserSettings> getSettingsFromSnapshot(Long snapshotId) {
        return auditLogRepository.getSnapshotData(snapshotId, AuditLogDescriptor.EntityType.USER_SETTINGS)
                .map(json -> mapper.fromJson(json, UserSettings.class));
    }

    public Optional<Long> findLastSnapshotId(Long advertisementId) {
        return auditLogRepository.findLastSnapshotId(advertisementId);
    }
}
