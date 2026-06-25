package org.ost.advertisement.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entity.Advertisement;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class AdvertisementService {

    private final AdvertisementRepository              repository;
    private final ComponentFactory<AuditPort>          auditPortFactory;
    private final ComponentFactory<AttachmentPort>     attachmentPortFactory;

    public List<AdvertisementInfoDto> getFiltered(@Valid @NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort) {
        return repository.findByFilter(filter, PageRequest.of(page, size, sort));
    }

    public int count(@Valid @NonNull AdvertisementFilterDto filter) {
        return repository.countByFilter(filter).intValue();
    }

    @Transactional
    public Long save(@NonNull AdvertisementSaveDto dto, @NonNull Long actingUserId) {
        boolean isNew = dto.id() == null;
        log.info("Advertisement save: id={}, isNew={}", dto.id(), isNew);
        Optional<Advertisement> before = isNew ? Optional.empty() : repository.findById(dto.id());
        Advertisement ad = buildEntity(dto, before.orElse(null));
        Advertisement saved = repository.save(ad);
        AdvertisementSnapshotDto savedSnapshot = new AdvertisementSnapshotDto(saved.getTitle(), saved.getDescription());
        if (isNew) {
            auditPortFactory.ifAvailable(p -> p.captureCreation(saved.getId(), savedSnapshot, actingUserId));
        } else {
            AdvertisementSnapshotDto beforeSnapshot = before
                    .map(b -> new AdvertisementSnapshotDto(b.getTitle(), b.getDescription()))
                    .orElse(new AdvertisementSnapshotDto(null, null));
            auditPortFactory.ifAvailable(p -> p.captureUpdate(saved.getId(), beforeSnapshot, savedSnapshot, actingUserId));
        }
        return saved.getId();
    }

    public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
        return repository.findAdvertisementById(id);
    }

    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return Set.copyOf(repository.findExistingIds(ids.toArray(new Long[0])));
    }

    public void onMediaChanged(@NonNull Long entityId) {
        attachmentPortFactory.ifAvailable(port ->
                repository.updateMedia(entityId, port.getMediaSummary(new EntityRef(EntityType.ADVERTISEMENT, entityId)))
        );
    }

    @Transactional
    public void delete(@NonNull Long id, @NonNull Long actingUserId) {
        log.info("Advertisement delete: id={}", id);
        repository.findById(id).ifPresent(entity -> {
            auditPortFactory.ifAvailable(p -> p.captureDeletion(id,
                    new AdvertisementSnapshotDto(entity.getTitle(), entity.getDescription()), actingUserId));
            attachmentPortFactory.ifAvailable(p -> p.softDeleteAll(new EntityRef(EntityType.ADVERTISEMENT, id), actingUserId));
        });
        repository.softDelete(id, actingUserId);
    }

    @Transactional
    public void cleanup(int retentionDays) {
        repository.deleteOlderThan(retentionDays);
    }

    private static Advertisement buildEntity(@NonNull AdvertisementSaveDto dto, Advertisement before) {
        return Advertisement.builder()
                .id(dto.id())
                .title(dto.title())
                .description(dto.description())
                .createdAt(before != null ? before.getCreatedAt() : null)
                .createdByUserId(before != null ? before.getCreatedByUserId() : null)
                .build();
    }
}
