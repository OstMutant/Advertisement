package org.ost.advertisement.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.api.AuditPort;
import org.ost.advertisement.audit.spi.AuditUserProvider;
import org.ost.advertisement.audit.api.AuditableSnapshot;
import org.ost.advertisement.audit.dto.SnapshotContent;
import org.ost.advertisement.audit.dto.UserSnapshotState;
import org.ost.advertisement.audit.model.AuditDiffEngine;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditLogRepository;
import org.ost.advertisement.core.model.ActionType;
import org.ost.advertisement.core.model.ChangeEntry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class DefaultAuditPort implements AuditPort {

    private final AuditDiffEngine                   diffEngine;
    private final AuditSnapshotMapper               snapshotMapper;
    private final AuditLogRepository                auditLogRepository;
    private final ObjectProvider<AuditUserProvider> auditUserProvider;
    private final AuditQueryService                 auditQueryService;

    private Long resolveActor(Long actorId) {
        if (actorId != null) return actorId;
        AuditUserProvider provider = auditUserProvider.getIfAvailable();
        return provider != null ? provider.getCurrentUserId().orElse(null) : null;
    }

    @Override
    @Transactional
    public void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId) {
        List<ChangeEntry> changes = diffEngine.diffFromNull(snapshot);
        auditLogRepository.insert(
                snapshot.entityType(),
                entityId,
                ActionType.CREATED,
                snapshotMapper.toJson(snapshot),
                snapshotMapper.toChangesJson(changes),
                resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId) {
        List<ChangeEntry> changes = diffEngine.diff(before, after);
        auditLogRepository.insert(
                after.entityType(),
                entityId,
                ActionType.UPDATED,
                snapshotMapper.toJson(after),
                snapshotMapper.toChangesJson(changes),
                resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId) {
        auditLogRepository.insert(
                snapshot.entityType(),
                entityId,
                ActionType.DELETED,
                snapshotMapper.toJson(snapshot),
                null,
                resolveActor(actorId));
    }

    @Override
    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId) {
        return auditQueryService.getSnapshotContent(snapshotId);
    }

    @Override
    public Optional<UserSnapshotState> getUserStateBefore(Long snapshotId) {
        return auditQueryService.getUserStateBefore(snapshotId);
    }

    @Override
    public Optional<UserSnapshotState> getUserStateAt(Long snapshotId) {
        return auditQueryService.getUserStateAt(snapshotId);
    }
}
