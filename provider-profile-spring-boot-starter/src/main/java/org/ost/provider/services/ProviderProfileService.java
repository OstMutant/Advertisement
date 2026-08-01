package org.ost.provider.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class ProviderProfileService {

    private static final PolicyFactory HTML_SANITIZER = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(new HtmlPolicyBuilder().allowElements("pre").toFactory());

    private final ProviderProfileRepository        repository;
    private final ComponentFactory<TaxonPort>      taxonPortFactory;
    private final ProviderProfileEnrichmentService enrichmentService;

    // ── Query & filter ───────────────────────────────────────────────────────

    public List<ProviderProfileDto> getFiltered(@Valid @NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale) {
        Optional<Set<Long>> categoryFilter = resolveCategoryFilter(filter);
        if (categoryFilter.filter(Set::isEmpty).isPresent()) {
            return List.of();
        }
        List<ProviderProfileDto> profiles = repository.findByFilter(filter, PageRequest.of(page, size, sort), categoryFilter.orElse(null));
        if (profiles.isEmpty()) return profiles;
        profiles = enrichmentService.enrichWithCategoriesAndCity(profiles, locale);
        return enrichmentService.enrichWithActorInfo(profiles);
    }

    public int count(@Valid @NonNull ProviderProfileFilterDto filter) {
        Optional<Set<Long>> categoryFilter = resolveCategoryFilter(filter);
        if (categoryFilter.filter(Set::isEmpty).isPresent()) {
            return 0;
        }
        return repository.countByFilter(filter, categoryFilter.orElse(null)).intValue();
    }

    private Optional<Set<Long>> resolveCategoryFilter(ProviderProfileFilterDto filter) {
        Set<Long> categoryIds = filter.getCategoryIds();
        if (categoryIds == null) {
            return Optional.empty();
        }
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findEntityIdsWithAnyTaxon(EntityType.PROVIDER_PROFILE, categoryIds));
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    @Transactional
    public Long save(@NonNull @Valid ProviderProfileSaveDto dto, @NonNull Long actingUserId, boolean actingUserIsPrivileged) {
        if (dto.kind() == ProviderKind.SUPPORT && !actingUserIsPrivileged) {
            throw new IllegalStateException("Only a privileged actor may set kind=SUPPORT");
        }
        log.info("ProviderProfile save: id={}, actorId={}, isNew={}", dto.id(), actingUserId, dto.id() == null);
        Optional<ProviderProfile> before = dto.id() == null ? Optional.empty() : repository.findById(dto.id());
        ProviderProfile entity = buildEntity(dto, actingUserId, before.orElse(null));
        Long id = repository.save(entity).getId();
        taxonPortFactory.ifAvailable(p -> p.replaceAssignments(EntityType.PROVIDER_PROFILE, id, dto.categoryIds() != null ? dto.categoryIds() : Set.of()));
        return id;
    }

    public Optional<ProviderProfileDto> findById(@NonNull Long id, @NonNull Locale locale) {
        return repository.findProviderProfileById(id).map(dto -> enrichSingle(dto, locale));
    }

    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId, @NonNull Locale locale) {
        return repository.findByActorId(actorId).map(dto -> enrichSingle(dto, locale));
    }

    private ProviderProfileDto enrichSingle(ProviderProfileDto dto, Locale locale) {
        ProviderProfileDto enriched = enrichmentService.enrichWithCategoryAndCity(dto, locale);
        return enrichmentService.enrichWithActor(enriched);
    }

    public Set<Long> findExistingIds(@NonNull Set<Long> ids) {
        return Set.copyOf(repository.findExistingIds(ids.toArray(new Long[0])));
    }

    public Set<Long> findOwnerIds(@NonNull Set<Long> userIds) {
        return repository.findOwnerIds(userIds);
    }

    @Transactional
    public void delete(@NonNull Long id, Long version) {
        log.info("ProviderProfile delete: id={}", id);
        repository.findById(id).ifPresent(_ -> taxonPortFactory.ifAvailable(
                p -> p.replaceAssignments(EntityType.PROVIDER_PROFILE, id, Set.of())));
        repository.delete(id, version);
    }

    // ── HTML sanitization ────────────────────────────────────────────────────

    private static ProviderProfile buildEntity(@NonNull ProviderProfileSaveDto dto, Long actingUserId, ProviderProfile before) {
        return ProviderProfile.builder()
                .id(dto.id())
                .actorId(before != null ? before.getActorId() : actingUserId)
                .kind(dto.kind())
                .about(sanitizeHtml(dto.about()))
                .cityTaxonId(dto.cityTaxonId())
                .createdAt(before != null ? before.getCreatedAt() : null)
                .version(dto.version())
                .build();
    }

    private static String sanitizeHtml(String html) {
        if (html == null || html.isBlank()) return html;
        String sanitized = HTML_SANITIZER.sanitize(html);
        validateAboutLength(sanitized);
        return sanitized;
    }

    private static void validateAboutLength(String html) {
        int textLength = Jsoup.parse(html).text().length();
        if (textLength > ProviderProfileSaveDto.ABOUT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "About text exceeds maximum length of "
                            + ProviderProfileSaveDto.ABOUT_MAX_LENGTH + " characters");
        }
    }
}
