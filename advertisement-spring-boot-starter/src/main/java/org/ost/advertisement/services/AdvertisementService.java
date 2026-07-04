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
import org.jsoup.Jsoup;
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
        if (ads.isEmpty()) return ads;
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
    public Long save(@NonNull @Valid AdvertisementSaveDto dto, @NonNull Long actingUserId) {
        log.info("Advertisement save: id={}, isNew={}", dto.id(), dto.id() == null);
        Optional<Advertisement> before = dto.id() == null ? Optional.empty() : repository.findById(dto.id());
        Advertisement ad = buildEntity(dto, before.orElse(null));
        return repository.save(ad).getId();
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
            List<Long> catIds = taxonPortFactory.findIfAvailable()
                    .map(p -> p.getForEntity(EntityType.ADVERTISEMENT, id, Locale.ENGLISH)
                            .stream().map(TaxonDto::getId).sorted().toList())
                    .orElse(List.of());
            Long attachmentSnapshotId = attachmentPortFactory.findIfAvailable()
                    .map(p -> p.getLatestSnapshotId(EntityType.ADVERTISEMENT, id))
                    .orElse(null);
            auditPortFactory.ifAvailable(p -> p.captureDeletion(id,
                    new AdvertisementSnapshotDto(entity.getTitle(), entity.getDescription(), catIds, attachmentSnapshotId), actingUserId));
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
        String sanitized = HTML_SANITIZER.sanitize(html);
        validateDescriptionLength(sanitized);
        return sanitized;
    }

    private static void validateDescriptionLength(String html) {
        int textLength = Jsoup.parse(html).text().length();
        if (textLength > AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Description text exceeds maximum length of "
                            + AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH + " characters");
        }
    }
}
