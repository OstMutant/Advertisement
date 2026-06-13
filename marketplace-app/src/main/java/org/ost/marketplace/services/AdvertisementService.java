package org.ost.marketplace.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.marketplace.entities.Advertisement;
import org.ost.marketplace.entities.EntityMarker;
import org.ost.user.entity.User;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.exceptions.authorization.AccessDeniedException;
import org.ost.marketplace.repository.advertisement.AdvertisementRepository;
import org.ost.marketplace.security.AccessEvaluator;
import org.ost.marketplace.dto.audit.AdvertisementSnapshotDto;
import org.ost.marketplace.services.auth.AuthContextService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class AdvertisementService {

    private final AdvertisementRepository    repository;
    private final AccessEvaluator            access;
    private final ComponentFactory<AuditPort>      auditPortFactory;
    private final ComponentFactory<AttachmentPort> attachmentPortFactory;
    private final AuthContextService         authContextService;

    public List<AdvertisementInfoDto> getFiltered(@Valid @NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort));
    }

    public int count(@Valid @NonNull AdvertisementFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public Advertisement save(@NonNull Advertisement ad) {
        if (ad.isNew() ? !access.isLoggedIn() : access.canNotEdit(ad)) {
            throw new AccessDeniedException("You cannot edit this advertisement");
        }
        boolean isNew = ad.isNew();
        log.info("Advertisement save: id={}, isNew={}", ad.getId(), isNew);
        Optional<Advertisement> before = isNew ? Optional.empty() : repository.findById(ad.getId());
        Advertisement saved = repository.save(ad);
        authContextService.getCurrentUser().map(User::getId).ifPresent(currentUserId -> {
            if (isNew) {
                auditPortFactory.ifAvailable(p -> p.captureCreation(saved.getId(), new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription()), currentUserId));
            } else {
                AdvertisementSnapshotDto beforeSnapshot = before
                        .map(b -> new AdvertisementSnapshotDto(b.getTitle(), b.getDescription()))
                        .orElse(new AdvertisementSnapshotDto(null, null));
                auditPortFactory.ifAvailable(p -> p.captureUpdate(saved.getId(), beforeSnapshot, new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription()), currentUserId));
            }
        });
        return saved;
    }

    public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
        return repository.findAdvertisementById(id);
    }

    public List<Long> findExistingIds(@NonNull Long[] ids) {
        return repository.findExistingIds(ids);
    }

    public void onMediaChanged(@NonNull Long entityId) {
        attachmentPortFactory.ifAvailable(port ->
                repository.updateMedia(entityId, port.getMediaSummary(new EntityRef(EntityType.ADVERTISEMENT, entityId)))
        );
    }

    @Transactional
    public boolean restore(@NonNull Long advertisementId, @NonNull Long snapshotId) {
        log.info("Advertisement restore: id={}, snapshotId={}", advertisementId, snapshotId);
        return repository.findById(advertisementId)
                .map(current -> {
                    if (access.canNotEdit(current)) throw new AccessDeniedException("You cannot edit this advertisement");
                    return auditPortFactory.findIfAvailable()
                            .flatMap(p -> p.<AdvertisementSnapshotDto>getSnapshotContent(snapshotId, EntityType.ADVERTISEMENT))
                            .map(content -> applyRestore(current, content))
                            .orElse(false);
                })
                .orElse(false);
    }

    private boolean applyRestore(@NonNull Advertisement current, @NonNull AuditSnapshotContentDto<AdvertisementSnapshotDto> content) {
        AdvertisementSnapshotDto restoredSnapshot = content.snapshotData();
        Advertisement restored = Advertisement.builder()
                .id(current.getId())
                .title(restoredSnapshot.title())
                .description(restoredSnapshot.description())
                .createdAt(current.getCreatedAt())
                .createdByUserId(current.getCreatedByUserId())
                .build();
        AdvertisementSnapshotDto beforeSnapshot = new AdvertisementSnapshotDto(current.getTitle(), current.getDescription());
        Advertisement saved = repository.save(restored);
        authContextService.getCurrentUser().map(User::getId).ifPresent(currentUserId -> {
            auditPortFactory.ifAvailable(p -> p.captureUpdate(saved.getId(), beforeSnapshot, new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription()), currentUserId));
            attachmentPortFactory.ifAvailable(p -> p.restoreToSnapshot(new EntityRef(EntityType.ADVERTISEMENT, saved.getId()), content.version(), currentUserId));
        });
        return true;
    }

    @Transactional
    public void cleanup(int retentionDays) {
        repository.deleteOlderThan(retentionDays);
    }

    @Transactional
    public void delete(@NonNull EntityMarker ad) {
        log.info("Advertisement delete: id={}", ad.getId());
        if (access.canNotDelete(ad)) {
            throw new AccessDeniedException("You cannot delete this advertisement");
        }
        Optional<Long> currentUserId = authContextService.getCurrentUser().map(User::getId);
        currentUserId.ifPresent(userId -> {
            repository.findById(ad.getId()).ifPresent(entity ->
                    auditPortFactory.ifAvailable(p -> p.captureDeletion(ad.getId(), new AdvertisementSnapshotDto(entity.getTitle(), entity.getDescription()), userId)));
            attachmentPortFactory.ifAvailable(p -> p.softDeleteAll(new EntityRef(EntityType.ADVERTISEMENT, ad.getId()), userId));
        });
        repository.softDelete(ad.getId(), currentUserId.orElse(null));
    }
}
