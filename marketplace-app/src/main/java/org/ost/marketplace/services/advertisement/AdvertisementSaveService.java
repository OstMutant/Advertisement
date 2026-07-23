package org.ost.marketplace.services.advertisement;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdvertisementSaveService {

    private final TransactionTemplate                 tx;
    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;
    private final ComponentFactory<AttachmentPort>    attachmentPortFactory;
    private final ComponentFactory<TaxonPort>         taxonPortFactory;
    private final ComponentFactory<AuditPort>         auditPortFactory;

    @SuppressWarnings("java:S4276")
    public Long save(@NonNull AdvertisementSaveDto dto, @NonNull Long actorId,
                     @NonNull Function<EntityRef, Long> commitGallery) {
        return tx.execute(status -> {
            boolean isNew = dto.id() == null;
            AdvertisementSnapshotDto before = isNew ? null : buildCurrentSnapshot(dto.id());

            Long savedId = advertisementPortFactory.get().save(dto);

            Set<Long> catIds = dto.categoryIds() != null ? dto.categoryIds() : Set.of();
            taxonPortFactory.ifAvailable(p -> p.replaceAssignments(EntityType.ADVERTISEMENT, savedId, catIds));

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
                    saved.getTitle(), saved.getDescription(), sortedCatIds, attachmentSnapshotId);

            if (isNew) {
                auditPortFactory.ifAvailable(p -> p.captureCreation(savedId, after, actorId));
            } else {
                auditPortFactory.ifAvailable(p -> p.captureUpdate(savedId, before, after, actorId));
            }
            log.info("Advertisement save transaction complete: id={}, isNew={}, categories={}",
                    savedId, isNew, catIds.size());
            return savedId;
        });
    }

    public void delete(@NonNull Long id, @NonNull Long actorId, Long version) {
        tx.executeWithoutResult(status -> {
            AdvertisementSnapshotDto snapshot = buildCurrentSnapshot(id);
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
        List<Long> catIds = taxonPortFactory.findIfAvailable()
                .map(p -> p.getForEntity(EntityType.ADVERTISEMENT, entityId, Locale.ENGLISH)
                        .stream().map(TaxonDto::getId).sorted().toList())
                .orElse(List.of());
        Long attachmentSnapshotId = attachmentPortFactory.findIfAvailable()
                .map(p -> p.getLatestSnapshotId(EntityType.ADVERTISEMENT, entityId))
                .orElse(null);
        return new AdvertisementSnapshotDto(ad.getTitle(), ad.getDescription(), catIds, attachmentSnapshotId);
    }
}
