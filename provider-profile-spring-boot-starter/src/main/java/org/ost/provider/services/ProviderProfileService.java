package org.ost.provider.services;

import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.provider.entity.ProviderProfile;
import org.ost.provider.repository.ProviderProfileRepository;
import org.ost.sanitizer.HtmlSanitizer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** CRUD for {@code provider_profile} -- does not write category/city assignments. */
@Slf4j
@Service
@RequiredArgsConstructor
@Validated
public class ProviderProfileService {

    private final ProviderProfileRepository        repository;
    private final ComponentFactory<TaxonPort>      taxonPortFactory;

    // ── Query & filter ───────────────────────────────────────────────────────

    public List<ProviderProfileDto> getFiltered(@Valid @NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        Optional<Set<Long>> taxonFilter = resolveCategoryAndCityFilter(filter);
        if (taxonFilter.filter(Set::isEmpty).isPresent()) {
            return List.of();
        }
        return repository.findByFilter(filter, PageRequest.of(page, size, sort), taxonFilter.orElse(null));
    }

    public int count(@Valid @NonNull ProviderProfileFilterDto filter) {
        Optional<Set<Long>> taxonFilter = resolveCategoryAndCityFilter(filter);
        if (taxonFilter.filter(Set::isEmpty).isPresent()) {
            return 0;
        }
        return repository.countByFilter(filter, taxonFilter.orElse(null)).intValue();
    }

    // AND-combines independently-resolved category/city constraints; empty() means no filter was requested.
    private Optional<Set<Long>> resolveCategoryAndCityFilter(ProviderProfileFilterDto filter) {
        Optional<Set<Long>> categoryConstraint = resolveCategoryFilter(filter);
        Optional<Set<Long>> cityConstraint = resolveCityFilter(filter);
        if (categoryConstraint.isEmpty()) return cityConstraint;
        if (cityConstraint.isEmpty()) return categoryConstraint;
        Set<Long> intersected = new HashSet<>(categoryConstraint.get());
        intersected.retainAll(cityConstraint.get());
        return Optional.of(intersected);
    }

    private Optional<Set<Long>> resolveCategoryFilter(ProviderProfileFilterDto filter) {
        return resolveTaxonIdFilter(filter.getCategoryIds());
    }

    private Optional<Set<Long>> resolveCityFilter(ProviderProfileFilterDto filter) {
        Long cityId = filter.getCityTaxonId();
        return resolveTaxonIdFilter(cityId == null ? null : Set.of(cityId));
    }

    private Optional<Set<Long>> resolveTaxonIdFilter(Set<Long> taxonIds) {
        if (taxonIds == null) {
            return Optional.empty();
        }
        return taxonPortFactory.findIfAvailable()
                .map(p -> p.findEntityIdsWithAnyTaxon(EntityType.PROVIDER_PROFILE, taxonIds));
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

    private static ProviderProfile buildEntity(@NonNull ProviderProfileSaveDto dto, Long targetUserId, ProviderProfile before) {
        return ProviderProfile.builder()
                .id(dto.id())
                .actorId(before != null ? before.getActorId() : targetUserId)
                .kind(dto.kind())
                .about(HtmlSanitizer.sanitize(dto.about(), ProviderProfileSaveDto.ABOUT_MAX_LENGTH))
                .createdAt(before != null ? before.getCreatedAt() : null)
                .version(dto.version())
                .build();
    }
}
