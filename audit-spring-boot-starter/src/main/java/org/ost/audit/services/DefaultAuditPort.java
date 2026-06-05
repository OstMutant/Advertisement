package org.ost.audit.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditDomainHook;
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
    private final AuditDomainHook    auditDomainHook;

    private Long resolveActor(Long actorId) {
        return Optional.ofNullable(actorId)
                .or(currentActorHook::getCurrentActorId)
                .orElse(null);
    }

    @Override
    @Transactional
    public void captureCreation(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId) {
        log.info("Audit capture: CREATED {} id={}", snapshot.entityType(), entityId);
        auditLogRepository.save(snapshot.entityType(), entityId, ActionType.CREATED,
                snapshot, resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureUpdate(@NonNull Long entityId, @NonNull AuditableSnapshot before, @NonNull AuditableSnapshot after, @NonNull Long actorId) {
        log.info("Audit capture: UPDATED {} id={}", after.entityType(), entityId);
        auditLogRepository.save(after.entityType(), entityId, ActionType.UPDATED,
                after, resolveActor(actorId));
    }

    @Override
    @Transactional
    public void captureDeletion(@NonNull Long entityId, @NonNull AuditableSnapshot snapshot, @NonNull Long actorId) {
        log.info("Audit capture: DELETED {} id={}", snapshot.entityType(), entityId);
        auditLogRepository.save(snapshot.entityType(), entityId, ActionType.DELETED,
                snapshot, resolveActor(actorId));
    }

    @Override
    public <T extends AuditableSnapshot> Optional<AuditSnapshotContentDto<T>> getSnapshotContent(@NonNull Long snapshotId, @NonNull EntityType entityType) {
        return auditLogRepository.getSnapshotContent(snapshotId, entityType)
                .flatMap(auditDomainHook::castIfKnown);
    }


}
