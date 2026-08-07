package org.ost.orchestrator.advertisement.save;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.orchestrator.shared.AttachmentSnapshotReaderService;
import org.ost.orchestrator.shared.AttachmentSoftDeleteService;
import org.ost.orchestrator.shared.TaxonAssignmentWriteService;
import org.ost.orchestrator.shared.TaxonLookupService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Application-level use case: save/delete an advertisement in one transaction, including its
 * category/city assignments, attachment gallery commit, and audit capture. 2 direct domain ports
 * (Advertisement + Audit) plus shared collaborators for the Taxon/Attachment steps — see
 * {@code marketplace-orchestrator/CLAUDE.md}'s "≤2 domain *Port types per class" constraint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementSaveService {

    private final TransactionTemplate                  tx;
    private final ComponentFactory<AdvertisementPort>  advertisementPortFactory;
    private final ComponentFactory<AuditPort>          auditPortFactory;
    private final TaxonLookupService                    taxonLookupService;
    private final TaxonAssignmentWriteService            taxonAssignmentWriteService;
    private final AttachmentSnapshotReaderService        attachmentSnapshotReaderService;
    private final AttachmentSoftDeleteService            attachmentSoftDeleteService;

    @SuppressWarnings("java:S4276")
    public Long save(@NonNull AdvertisementSaveDto dto, @NonNull Long actorId,
                     @NonNull Function<EntityRef, Long> commitGallery) {
        return tx.execute(status -> {
            boolean isNew = dto.id() == null;
            AdvertisementSnapshotDto before = isNew ? null : buildCurrentSnapshot(dto.id());

            Long savedId = advertisementPortFactory.get().save(dto);

            Set<Long> catIds = dto.categoryIds() != null ? dto.categoryIds() : Set.of();
            Long cityId = dto.cityTaxonId();
            // replaceAssignments() diff-replaces ALL taxon types at once -- ids must be unioned into one call.
            Set<Long> assignmentIds = cityId != null
                    ? Stream.concat(catIds.stream(), Stream.of(cityId)).collect(Collectors.toSet())
                    : catIds;
            taxonAssignmentWriteService.replace(EntityType.ADVERTISEMENT, savedId, assignmentIds);

            // Last mutation before commit -- shrinks the window for a post-move rollback to orphan S3 files.
            EntityRef entityRef = new EntityRef(EntityType.ADVERTISEMENT, savedId);
            Long gallerySnapshotId = commitGallery.apply(entityRef);
            Long attachmentSnapshotId;
            if (gallerySnapshotId != null) {
                attachmentSnapshotId = gallerySnapshotId;
            } else {
                attachmentSnapshotId = before != null ? before.attachmentSnapshotId() : null;
            }
            registerOrphanWarningOnRollback(entityRef, gallerySnapshotId);

            AdvertisementInfoDto saved = advertisementPortFactory.get().findById(savedId).orElseThrow();
            List<Long> sortedCatIds = catIds.stream().sorted().toList();
            AdvertisementSnapshotDto after = new AdvertisementSnapshotDto(
                    saved.getTitle(), saved.getDescription(), saved.getAdKind(), sortedCatIds, cityId, attachmentSnapshotId);

            if (isNew) {
                auditPortFactory.ifAvailable(p -> p.captureCreation(savedId, after, actorId));
            } else if (before != null) {
                auditPortFactory.ifAvailable(p -> p.captureUpdate(savedId, after, actorId));
            } else {
                log.warn("Advertisement {} updated but no 'before' snapshot was available (concurrent delete?) - skipping audit capture", savedId);
            }
            log.info("Advertisement save transaction complete: id={}, isNew={}, categories={}",
                    savedId, isNew, catIds.size());
            return savedId;
        });
    }

    public void delete(@NonNull Long id, @NonNull Long actorId, Long version) {
        tx.executeWithoutResult(status -> {
            AdvertisementSnapshotDto snapshot = buildCurrentSnapshot(id);
            if (snapshot != null) {
                taxonAssignmentWriteService.clear(EntityType.ADVERTISEMENT, id);
                attachmentSoftDeleteService.softDeleteAll(new EntityRef(EntityType.ADVERTISEMENT, id), actorId);
            }
            advertisementPortFactory.get().delete(id, actorId, version);
            if (snapshot != null) {
                auditPortFactory.ifAvailable(p -> p.captureDeletion(id, snapshot, actorId));
            }
        });
    }

    // isSynchronizationActive() guard is required -- registerSynchronization() throws outside a real transaction.
    private void registerOrphanWarningOnRollback(EntityRef entityRef, Long gallerySnapshotId) {
        if (gallerySnapshotId == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    log.error("Advertisement save rolled back after attachment gallery commit for {} — "
                            + "S3 files may be orphaned (no attachment row was persisted); "
                            + "AttachmentCleanupService's scheduled sweep will reap them", entityRef);
                }
            }
        });
    }

    private AdvertisementSnapshotDto buildCurrentSnapshot(@NonNull Long entityId) {
        AdvertisementInfoDto ad = advertisementPortFactory.get().findById(entityId).orElse(null);
        if (ad == null) return null;
        List<TaxonDto> assignments = taxonLookupService.getForEntity(EntityType.ADVERTISEMENT, entityId, Locale.ENGLISH);
        List<Long> catIds = assignments.stream()
                .filter(t -> t.getType() == TaxonType.CATEGORY)
                .map(TaxonDto::getId).sorted().toList();
        Long cityId = assignments.stream()
                .filter(t -> t.getType() == TaxonType.CITY)
                .map(TaxonDto::getId).findFirst().orElse(null);
        Long attachmentSnapshotId = attachmentSnapshotReaderService.getLatestSnapshotId(EntityType.ADVERTISEMENT, entityId);
        return new AdvertisementSnapshotDto(ad.getTitle(), ad.getDescription(), ad.getAdKind(), catIds, cityId, attachmentSnapshotId);
    }
}
