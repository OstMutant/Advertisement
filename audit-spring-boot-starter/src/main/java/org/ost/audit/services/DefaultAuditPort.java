package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.audit.model.AuditDiffEngine;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class DefaultAuditPort implements AuditPort {

    private final AuditDiffEngine                      diffEngine;
    private final AuditSnapshotMapper                  snapshotMapper;
    private final AuditLogRepository                   auditLogRepository;
    private final ObjectProvider<CurrentActorHook> currentActorHook;
    private final AuditQueryService                    auditQueryService;
    private final AuditHistoryService                  auditHistoryService;

    private Long resolveActor(Long actorId) {
        if (actorId != null) return actorId;
        CurrentActorHook hook = currentActorHook.getIfAvailable();
        return hook != null ? hook.getCurrentActorId().orElse(null) : null;
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
    public Optional<SnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditQueryService.getSnapshotContent(snapshotId, entityType);
    }

    @Override
    public Optional<SnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditQueryService.getPreviousSnapshotContent(snapshotId, entityType);
    }

    @Override
    public void appendNoteToLastSnapshot(EntityType entityType, Long entityId, String note) {
        auditHistoryService.appendNoteToLastSnapshot(entityType, entityId, note);
    }
}
