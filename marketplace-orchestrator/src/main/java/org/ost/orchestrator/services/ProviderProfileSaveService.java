package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.orchestrator.spi.CurrentLocaleHook;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Application-level use case: save/delete a provider profile in one transaction, including its
 * category assignment and audit capture. 2 direct domain ports (ProviderProfile + Audit) plus the
 * shared {@link TaxonAssignmentWriteService} collaborator -- see
 * marketplace-orchestrator/CLAUDE.md's "≤2 domain *Port types per class" constraint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderProfileSaveService {

    private final TransactionTemplate                    tx;
    private final ComponentFactory<ProviderProfilePort>  providerProfilePortFactory;
    private final ComponentFactory<AuditPort>             auditPortFactory;
    private final TaxonAssignmentWriteService              taxonAssignmentWriteService;
    private final ProviderProfileDisplayEnrichmentService  displayEnrichmentService;
    private final CurrentLocaleHook                        currentLocaleHook;

    public Long save(@NonNull ProviderProfileSaveDto dto, @NonNull Long targetUserId, @NonNull Long actorId, boolean actorIsPrivileged) {
        return tx.execute(status -> {
            boolean isNew = dto.id() == null;
            ProviderProfileSnapshotDto before = isNew ? null : buildCurrentSnapshot(dto.id());
            if (!isNew && before == null) {
                throw new OptimisticLockingFailureException(
                        "Provider profile " + dto.id() + " was deleted before this edit could be saved");
            }

            Long savedId = providerProfilePortFactory.get().save(dto, targetUserId, actorId, actorIsPrivileged);

            Set<Long> catIds = dto.categoryIds() != null ? dto.categoryIds() : Set.of();
            taxonAssignmentWriteService.replace(EntityType.PROVIDER_PROFILE, savedId, catIds);

            ProviderProfileDto saved = displayEnrichmentService.enrichWithCategoryAndCity(
                    providerProfilePortFactory.get().findById(savedId).orElseThrow(), currentLocaleHook.getCurrentLocale());
            ProviderProfileSnapshotDto after = new ProviderProfileSnapshotDto(
                    saved.getKind(), saved.getAbout(), sortedList(saved.getCategoryIds()), saved.getCityTaxonId());

            captureAudit(isNew, savedId, after, actorId);
            log.info("ProviderProfile save transaction complete: id={}, isNew={}, categories={}",
                    savedId, isNew, catIds.size());
            return savedId;
        });
    }

    private void captureAudit(boolean isNew, Long savedId, ProviderProfileSnapshotDto after, Long actorId) {
        if (isNew) {
            auditPortFactory.ifAvailable(p -> p.captureCreation(savedId, after, actorId));
        } else {
            auditPortFactory.ifAvailable(p -> p.captureUpdate(savedId, after, actorId));
        }
    }

    public boolean isAvailable() {
        return providerProfilePortFactory.findIfAvailable().isPresent();
    }

    public Optional<ProviderProfileDto> findById(@NonNull Long id) {
        return providerProfilePortFactory.get().findById(id)
                .map(p -> displayEnrichmentService.enrichWithCategoryAndCity(p, currentLocaleHook.getCurrentLocale()));
    }

    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) {
        return providerProfilePortFactory.get().findByActorId(actorId)
                .map(p -> displayEnrichmentService.enrichWithCategoryAndCity(p, currentLocaleHook.getCurrentLocale()));
    }

    public void delete(@NonNull Long id, @NonNull Long actorId, Long version) {
        tx.executeWithoutResult(status -> {
            ProviderProfileSnapshotDto snapshot = buildCurrentSnapshot(id);
            if (snapshot != null) {
                taxonAssignmentWriteService.clear(EntityType.PROVIDER_PROFILE, id);
            }
            providerProfilePortFactory.get().delete(id, version);
            if (snapshot != null) {
                auditPortFactory.ifAvailable(p -> p.captureDeletion(id, snapshot, actorId));
            }
        });
    }

    private ProviderProfileSnapshotDto buildCurrentSnapshot(@NonNull Long entityId) {
        ProviderProfileDto p = providerProfilePortFactory.get().findById(entityId)
                .map(dto -> displayEnrichmentService.enrichWithCategoryAndCity(dto, currentLocaleHook.getCurrentLocale()))
                .orElse(null);
        if (p == null) return null;
        return new ProviderProfileSnapshotDto(p.getKind(), p.getAbout(), sortedList(p.getCategoryIds()), p.getCityTaxonId());
    }

    private static List<Long> sortedList(Set<Long> ids) {
        return ids != null ? ids.stream().sorted().toList() : List.of();
    }
}
