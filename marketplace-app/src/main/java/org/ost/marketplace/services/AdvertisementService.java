package org.ost.marketplace.services;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.marketplace.dto.AdvertisementInfoDto;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.marketplace.entities.Advertisement;
import org.ost.marketplace.entities.EntityMarker;
import org.ost.marketplace.entities.User;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.ui.ComponentFactory;
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

    public List<AdvertisementInfoDto> getFiltered(@Valid AdvertisementFilterDto filter, int page, int size, Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort));
    }

    public int count(@Valid AdvertisementFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public Advertisement save(Advertisement ad) {
        if (ad.isNew() ? !access.isLoggedIn() : access.canNotEdit(ad)) {
            throw new AccessDeniedException("You cannot edit this advertisement");
        }
        boolean isNew = ad.isNew();
        log.info("Advertisement save: id={}, isNew={}", ad.getId(), isNew);
        Advertisement before = isNew ? null : repository.findById(ad.getId()).orElse(null);
        Advertisement saved = repository.save(ad);
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            if (isNew) {
                auditPortFactory.ifAvailable(p -> p.captureCreation(saved.getId(), new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription()), currentUserId));
            } else {
                AdvertisementSnapshotDto beforeSnapshot = before != null ? new AdvertisementSnapshotDto(before.getTitle(), before.getDescription()) : new AdvertisementSnapshotDto(null, null);
                auditPortFactory.ifAvailable(p -> p.captureUpdate(saved.getId(), beforeSnapshot, new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription()), currentUserId));
            }
        }
        return saved;
    }

    public Optional<AdvertisementInfoDto> findById(Long id) {
        return repository.findAdvertisementById(id);
    }

    public List<Long> findExistingIds(Long[] ids) {
        return repository.findExistingIds(ids);
    }

    public void onMediaChanged(Long entityId) {
        attachmentPortFactory.ifAvailable(port ->
                repository.updateMedia(entityId, port.getMediaSummary(new EntityRef(EntityType.ADVERTISEMENT, entityId)))
        );
    }

    public String resolveDisplayName(AuditableSnapshot snapshot) {
        return snapshot instanceof AdvertisementSnapshotDto ad ? ad.title() : "";
    }

    @Transactional
    public boolean restore(Long advertisementId, Long snapshotId) {
        log.info("Advertisement restore: id={}, snapshotId={}", advertisementId, snapshotId);
        Advertisement current = repository.findById(advertisementId).orElse(null);
        if (current == null) return false;
        if (access.canNotEdit(current)) throw new AccessDeniedException("You cannot edit this advertisement");
        AuditSnapshotContentDto content = Optional.ofNullable(auditPortFactory.getIfAvailable())
                .flatMap(p -> p.getSnapshotContent(snapshotId, EntityType.ADVERTISEMENT))
                .orElse(null);
        if (content == null) return false;
        if (!(content.snapshotData() instanceof AdvertisementSnapshotDto restoredSnapshot)) return false;
        Advertisement restored = Advertisement.builder()
                .id(current.getId())
                .title(restoredSnapshot.title())
                .description(restoredSnapshot.description())
                .createdAt(current.getCreatedAt())
                .createdByUserId(current.getCreatedByUserId())
                .build();
        AdvertisementSnapshotDto beforeSnapshot = new AdvertisementSnapshotDto(current.getTitle(), current.getDescription());
        Advertisement saved = repository.save(restored);
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            auditPortFactory.ifAvailable(p -> p.captureUpdate(saved.getId(), beforeSnapshot, new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription()), currentUserId));
            attachmentPortFactory.ifAvailable(p -> p.restoreToSnapshot(new EntityRef(EntityType.ADVERTISEMENT, saved.getId()), content.version(), currentUserId));
        }
        return true;
    }

    @Transactional
    public void cleanup(int retentionDays) {
        repository.deleteOlderThan(retentionDays);
    }

    @Transactional
    public void delete(EntityMarker ad) {
        log.info("Advertisement delete: id={}", ad.getId());
        if (access.canNotDelete(ad)) {
            throw new AccessDeniedException("You cannot delete this advertisement");
        }
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            repository.findById(ad.getId()).ifPresent(entity ->
                    auditPortFactory.ifAvailable(p -> p.captureDeletion(ad.getId(), new AdvertisementSnapshotDto(entity.getTitle(), entity.getDescription()), currentUserId)));
        }
        if (currentUserId != null) {
            attachmentPortFactory.ifAvailable(p -> p.softDeleteAll(new EntityRef(EntityType.ADVERTISEMENT, ad.getId()), currentUserId));
        }
        repository.softDelete(ad.getId(), currentUserId);
    }
}
