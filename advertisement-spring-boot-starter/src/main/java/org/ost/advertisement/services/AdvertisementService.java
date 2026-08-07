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
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.jsoup.Jsoup;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class AdvertisementService {

    private static final PolicyFactory HTML_SANITIZER = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(new HtmlPolicyBuilder().allowElements("pre").toFactory());

    private final AdvertisementRepository          repository;
    private final ComponentFactory<TaxonPort>      taxonPortFactory;

    // ── Query & filter ───────────────────────────────────────────────────────

    public List<AdvertisementInfoDto> getFiltered(@Valid @NonNull AdvertisementFilterDto filter, int page, int size, @NonNull Sort sort) {
        Optional<Set<Long>> taxonFilter = resolveCategoryAndCityFilter(filter);
        if (taxonFilter.filter(Set::isEmpty).isPresent()) {
            return List.of();
        }
        return repository.findByFilter(filter, PageRequest.of(page, size, sort), taxonFilter.orElse(null));
    }

    public int count(@Valid @NonNull AdvertisementFilterDto filter) {
        Optional<Set<Long>> taxonFilter = resolveCategoryAndCityFilter(filter);
        if (taxonFilter.filter(Set::isEmpty).isPresent()) {
            return 0;
        }
        return repository.countByFilter(filter, taxonFilter.orElse(null)).intValue();
    }

    // AND-combines independently-resolved category/city constraints; empty() means no filter was requested.
    private Optional<Set<Long>> resolveCategoryAndCityFilter(AdvertisementFilterDto filter) {
        Optional<Set<Long>> categoryConstraint = resolveCategoryFilter(filter);
        Optional<Set<Long>> cityConstraint = resolveCityFilter(filter);
        if (categoryConstraint.isEmpty()) return cityConstraint;
        if (cityConstraint.isEmpty()) return categoryConstraint;
        Set<Long> intersected = new HashSet<>(categoryConstraint.get());
        intersected.retainAll(cityConstraint.get());
        return Optional.of(intersected);
    }

    private Optional<Set<Long>> resolveCategoryFilter(AdvertisementFilterDto filter) {
        return resolveTaxonIdFilter(filter.getCategoryIds());
    }

    private Optional<Set<Long>> resolveCityFilter(AdvertisementFilterDto filter) {
        Long cityId = filter.getCityTaxonId();
        return resolveTaxonIdFilter(cityId == null ? null : Set.of(cityId));
    }

    private Optional<Set<Long>> resolveTaxonIdFilter(Set<Long> taxonIds) {
        if (taxonIds == null) {
            return Optional.empty();
        }
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT, taxonIds));
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public Long save(@NonNull @Valid AdvertisementSaveDto dto) {
        log.info("Advertisement save: id={}, isNew={}", dto.id(), dto.id() == null);
        Optional<Advertisement> before = dto.id() == null ? Optional.empty() : repository.findById(dto.id());
        Advertisement ad = buildEntity(dto, before.orElse(null));
        return repository.save(ad).getId();
    }

    public Optional<AdvertisementInfoDto> findById(@NonNull Long id) {
        return repository.findAdvertisementById(id);
    }

    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return Set.copyOf(repository.findExistingIds(ids.toArray(new Long[0])));
    }

    public List<AdvertisementInfoDto> findByCreator(@NonNull Long userId) {
        return repository.findByCreator(userId);
    }

    public Set<Long> findOwnerIds(@NonNull Set<Long> userIds) {
        return repository.findOwnerIds(userIds);
    }

    @Transactional
    public void clearActorReferences(@NonNull Set<Long> userIds) {
        repository.clearActorReferences(userIds);
    }

    public void delete(@NonNull Long id, @NonNull Long actingUserId, Long version) {
        log.info("Advertisement delete: id={}", id);
        repository.softDelete(id, actingUserId, version);
    }

    @Transactional
    public void cleanup(int retentionDays) {
        log.info("Advertisement cleanup started: retentionDays={}", retentionDays);
        int deleted = repository.deleteOlderThan(retentionDays);
        log.info("Advertisement cleanup finished: deletedRows={}", deleted);
    }

    // ── HTML sanitization ────────────────────────────────────────────────────

    private static Advertisement buildEntity(@NonNull AdvertisementSaveDto dto, Advertisement before) {
        return Advertisement.builder()
                .id(dto.id())
                .title(dto.title())
                .description(sanitizeHtml(dto.description()))
                .adKind(dto.adKind())
                .createdAt(before != null ? before.getCreatedAt() : null)
                .createdBy(before != null ? before.getCreatedBy() : null)
                .version(dto.version())
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
