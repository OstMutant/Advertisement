package org.ost.advertisement.services;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.entities.ActionType;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.EntityMarker;
import org.ost.advertisement.entities.EntityType;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.advertisement.AdvertisementRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Validated
public class AdvertisementService {

    private final AdvertisementRepository           repository;
    private final AccessEvaluator                   access;
    private final ObjectProvider<AttachmentService> attachmentService;
    private final SnapshotService                   snapshotService;
    private final AuthContextService                authContextService;

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
        Advertisement saved = repository.save(ad);
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            snapshotService.captureAdvertisement(saved, isNew ? ActionType.CREATED : ActionType.UPDATED, currentUserId);
        }
        return saved;
    }

    public Optional<AdvertisementInfoDto> findById(Long id) {
        return repository.findAdvertisementById(id);
    }

    @Transactional
    public boolean restore(Long advertisementId, Long snapshotId) {
        Advertisement current = repository.findById(advertisementId).orElse(null);
        if (current == null) return false;
        if (access.canNotEdit(current)) throw new AccessDeniedException("You cannot edit this advertisement");
        SnapshotService.SnapshotContent content = snapshotService.getSnapshotContent(snapshotId).orElse(null);
        if (content == null) return false;
        Advertisement restored = Advertisement.builder()
                .id(current.getId())
                .title(content.title())
                .description(content.description())
                .createdAt(current.getCreatedAt())
                .createdByUserId(current.getCreatedByUserId())
                .build();
        Advertisement saved = repository.save(restored);
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            attachmentService.ifAvailable(s -> s.restoreToUrls(saved.getId(), content.attachmentUrls(), currentUserId));
            snapshotService.captureAdvertisement(saved, ActionType.UPDATED, currentUserId);
        }
        return true;
    }

    @Transactional
    public void delete(EntityMarker ad) {
        if (access.canNotDelete(ad)) {
            throw new AccessDeniedException("You cannot delete this advertisement");
        }
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        repository.findById(ad.getId()).ifPresent(entity ->
                snapshotService.captureAdvertisement(entity, ActionType.DELETED, currentUserId));
        attachmentService.ifAvailable(s -> s.softDeleteAll(EntityType.ADVERTISEMENT, ad.getId(), currentUserId));
        repository.softDelete(ad.getId(), currentUserId);
    }
}
