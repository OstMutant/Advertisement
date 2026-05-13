package org.ost.advertisement.services;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.AuditPort;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.entities.Advertisement;
import org.ost.advertisement.entities.EntityMarker;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.events.AdvertisementDeletedEvent;
import org.ost.advertisement.events.AdvertisementRestoredEvent;
import org.ost.advertisement.exceptions.authorization.AccessDeniedException;
import org.ost.advertisement.repository.advertisement.AdvertisementRepository;
import org.ost.advertisement.audit.repository.AuditLogRepository;
import org.ost.advertisement.security.AccessEvaluator;
import org.ost.advertisement.services.audit.AdvertisementSnapshot;
import org.ost.advertisement.audit.services.AuditQueryService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.springframework.context.ApplicationEventPublisher;
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

    private final AdvertisementRepository    repository;
    private final AccessEvaluator            access;
    private final AuditPort                  auditPort;
    private final AuditQueryService          auditQueryService;
    private final AuthContextService         authContextService;
    private final ApplicationEventPublisher  context;

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
        Advertisement before = isNew ? null : repository.findById(ad.getId()).orElse(null);
        Advertisement saved = repository.save(ad);
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            if (isNew) {
                auditPort.captureCreation(saved.getId(), new AdvertisementSnapshot(saved.getTitle(), saved.getDescription()), currentUserId);
            } else {
                AdvertisementSnapshot beforeSnapshot = before != null ? new AdvertisementSnapshot(before.getTitle(), before.getDescription()) : new AdvertisementSnapshot(null, null);
                auditPort.captureUpdate(saved.getId(), beforeSnapshot, new AdvertisementSnapshot(saved.getTitle(), saved.getDescription()), currentUserId);
            }
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
        AuditLogRepository.SnapshotContent content = auditQueryService.getSnapshotContent(snapshotId).orElse(null);
        if (content == null) return false;
        Advertisement restored = Advertisement.builder()
                .id(current.getId())
                .title(content.title())
                .description(content.description())
                .createdAt(current.getCreatedAt())
                .createdByUserId(current.getCreatedByUserId())
                .build();
        AdvertisementSnapshot beforeSnapshot = new AdvertisementSnapshot(current.getTitle(), current.getDescription());
        Advertisement saved = repository.save(restored);
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            auditPort.captureUpdate(saved.getId(), beforeSnapshot, new AdvertisementSnapshot(saved.getTitle(), saved.getDescription()), currentUserId);
            context.publishEvent(new AdvertisementRestoredEvent(saved.getId(), content.version(), currentUserId));
        }
        return true;
    }

    @Transactional
    public void delete(EntityMarker ad) {
        if (access.canNotDelete(ad)) {
            throw new AccessDeniedException("You cannot delete this advertisement");
        }
        Long currentUserId = authContextService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId != null) {
            repository.findById(ad.getId()).ifPresent(entity ->
                    auditPort.captureDeletion(ad.getId(), new AdvertisementSnapshot(entity.getTitle(), entity.getDescription()), currentUserId));
        }
        if (currentUserId != null) {
            context.publishEvent(new AdvertisementDeletedEvent(ad.getId(), currentUserId));
        }
        repository.softDelete(ad.getId(), currentUserId);
    }
}
