package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultAuditPort implements AuditPort {

    private final AuditLogRepository auditLogRepository;
    private final CurrentActorHook   currentActorHook;

    private Long resolveActor(Long actorId) {
        if (actorId != null) return actorId;
        return currentActorHook.getCurrentActorId().orElse(null);
    }

    @Override
    @Transactional
    public void captureCreation(Long entityId, AuditableSnapshot snapshot, Long actorId) {
        log.info("Audit capture: CREATED {} id={}", snapshot.entityType(), entityId);
        auditLogRepository.save(snapshot.entityType(), entityId, ActionType.CREATED,
                snapshot, resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureUpdate(Long entityId, AuditableSnapshot before, AuditableSnapshot after, Long actorId) {
        log.info("Audit capture: UPDATED {} id={}", after.entityType(), entityId);
        auditLogRepository.save(after.entityType(), entityId, ActionType.UPDATED,
                after, resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureDeletion(Long entityId, AuditableSnapshot snapshot, Long actorId) {
        log.info("Audit capture: DELETED {} id={}", snapshot.entityType(), entityId);
        auditLogRepository.save(snapshot.entityType(), entityId, ActionType.DELETED,
                snapshot, resolveActor(actorId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditLogRepository.getSnapshotContent(snapshotId, entityType)
                .map(c -> (AuditSnapshotContentDto<T>) c);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return auditLogRepository.getPreviousSnapshotContent(snapshotId, entityType)
                .map(c -> (AuditSnapshotContentDto<T>) c);
    }

}
