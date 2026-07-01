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
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.spi.TaxonPort;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class AdvertisementService {

    private static final PolicyFactory HTML_SANITIZER = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS);

    private final AdvertisementRepository          repository;
    private final ComponentFactory<AuditPort>      auditPortFactory;
    private final ComponentFactory<AttachmentPort> attachmentPortFactory;
    private final ComponentFactory<TaxonPort>      taxonPortFactory;

    public List<AdvertisementInfoDto> getFiltered(@Valid @NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale) {
        Set<Long> allowedIds = resolveCategoryFilter(filter);
        if (allowedIds != null && allowedIds.isEmpty()) {
            return List.of();
        }
        List<AdvertisementInfoDto> ads = repository.findByFilter(filter, PageRequest.of(page, size, sort), allowedIds);
        return enrichWithCategories(ads, locale);
    }

    public int count(@Valid @NonNull AdvertisementFilterDto filter) {
        Set<Long> allowedIds = resolveCategoryFilter(filter);
        if (allowedIds != null && allowedIds.isEmpty()) {
            return 0;
        }
        return repository.countByFilter(filter, allowedIds).intValue();
    }

    private Set<Long> resolveCategoryFilter(AdvertisementFilterDto filter) {
        if (filter.getCategoryIds() == null) {
            return null;
        }
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT, filter.getCategoryIds()))
                .orElse(null);
    }

    private List<AdvertisementInfoDto> enrichWithCategories(List<AdvertisementInfoDto> ads, Locale locale) {
        if (ads.isEmpty()) return ads;
        return taxonPortFactory.findIfAvailable()
                .map(taxonPort -> {
                    Set<Long> ids = ads.stream().map(AdvertisementInfoDto::getId).collect(Collectors.toSet());
                    Map<Long, List<TaxonDto>> categoryMap = taxonPort.getForEntities(EntityType.ADVERTISEMENT, ids, locale);
                    return ads.stream()
                            .map(ad -> {
                                List<TaxonDto> cats = categoryMap.getOrDefault(ad.getId(), List.of());
                                Set<Long> catIds = cats.stream().map(TaxonDto::getId).collect(Collectors.toSet());
                                List<String> catNames = cats.stream().map(TaxonDto::getName).toList();
                                return ad.toBuilder().categoryIds(catIds).categoryNames(catNames).build();
                            })
                            .toList();
                })
                .orElse(ads);
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
        if (dto.categoryIds() != null) {
            saveCategoryChanges(saved.getId(), dto.categoryIds());
        }
        return saved.getId();
    }

    private void saveCategoryChanges(Long entityId, Set<Long> newCategoryIds) {
        taxonPortFactory.ifAvailable(taxonPort ->
                taxonPort.replaceAssignments(EntityType.ADVERTISEMENT, entityId, newCategoryIds));
    }

    public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
        return repository.findAdvertisementById(id).map(dto ->
                taxonPortFactory.findIfAvailable()
                        .map(taxonPort -> {
                            Set<Long> catIds = taxonPort.getForEntity(EntityType.ADVERTISEMENT, id, Locale.ENGLISH)
                                    .stream().map(TaxonDto::getId).collect(Collectors.toSet());
                            return dto.toBuilder().categoryIds(catIds).build();
                        })
                        .orElse(dto)
        );
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
            taxonPortFactory.ifAvailable(p -> p.replaceAssignments(EntityType.ADVERTISEMENT, id, Set.of()));
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
                .description(sanitizeHtml(dto.description()))
                .createdAt(before != null ? before.getCreatedAt() : null)
                .createdByUserId(before != null ? before.getCreatedByUserId() : null)
                .build();
    }

    private static String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return html;
        return HTML_SANITIZER.sanitize(html);
    }
}
