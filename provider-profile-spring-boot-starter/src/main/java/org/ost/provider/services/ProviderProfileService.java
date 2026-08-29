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

    // ── Query & filter ───────────────────────────────────────────────────────

    public List<ProviderProfileDto> getFiltered(@Valid @NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        Optional<Set<Long>> categoryFilter = resolveCategoryFilter(filter);
        if (categoryFilter.filter(Set::isEmpty).isPresent()) {
            return List.of();
        }
        return repository.findByFilter(filter, PageRequest.of(page, size, sort), categoryFilter.orElse(null));
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
    public Long save(@NonNull @Valid ProviderProfileSaveDto dto, @NonNull Long targetUserId, @NonNull Long actingUserId, boolean actingUserIsPrivileged) {
        if (dto.kind() == ProviderKind.SUPPORT && !actingUserIsPrivileged) {
            throw new IllegalStateException("Only a privileged actor may set kind=SUPPORT");
        }
        log.info("ProviderProfile save: id={}, targetUserId={}, actingUserId={}, isNew={}", dto.id(), targetUserId, actingUserId, dto.id() == null);
        Optional<ProviderProfile> before = dto.id() == null ? Optional.empty() : repository.findById(dto.id());
        ProviderProfile entity = buildEntity(dto, targetUserId, before.orElse(null));
        return repository.save(entity).getId();
    }

    public Optional<ProviderProfileDto> findById(@NonNull Long id) {
        return repository.findProviderProfileById(id);
    }

    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) {
        return repository.findByActorId(actorId);
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
        repository.delete(id, version);
    }

    // ── HTML sanitization ────────────────────────────────────────────────────

    private static ProviderProfile buildEntity(@NonNull ProviderProfileSaveDto dto, Long targetUserId, ProviderProfile before) {
        return ProviderProfile.builder()
                .id(dto.id())
                .actorId(before != null ? before.getActorId() : targetUserId)
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
