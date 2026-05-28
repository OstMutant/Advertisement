package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class DefaultAuditPort implements AuditPort {

    private final AuditDiffService                     diffEngine;
    private final AuditLogRepository                   auditLogRepository;
    private final ObjectProvider<CurrentActorHook>     currentActorHook;
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
        log.info("Audit capture: CREATED {} id={}", snapshot.entityType(), entityId);
        auditLogRepository.save(snapshot.entityType(), entityId, ActionType.CREATED,
                snapshot, diffEngine.diffFromNull(snapshot), resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId) {
        log.info("Audit capture: UPDATED {} id={}", after.entityType(), entityId);
        auditLogRepository.save(after.entityType(), entityId, ActionType.UPDATED,
                after, diffEngine.diff(before, after), resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId) {
        log.info("Audit capture: DELETED {} id={}", snapshot.entityType(), entityId);
        auditLogRepository.save(snapshot.entityType(), entityId, ActionType.DELETED,
                snapshot, null, resolveActor(actorId));
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
