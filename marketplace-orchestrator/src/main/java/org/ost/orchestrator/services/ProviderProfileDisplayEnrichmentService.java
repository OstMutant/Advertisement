package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles the display-only fields of {@link ProviderProfileDto} (category/city names, actor
 * name/email) from the Taxon and User ports.
 */
@Service
@RequiredArgsConstructor
public class ProviderProfileDisplayEnrichmentService {

    private final TaxonLookupService taxonLookupService;
    private final ActorLookupService actorLookupService;

    // ── Categories & city ────────────────────────────────────────────────────

    public List<ProviderProfileDto> enrichWithCategoriesAndCity(@NonNull List<ProviderProfileDto> profiles, @NonNull Locale locale) {
        Set<Long> ids = profiles.stream().map(ProviderProfileDto::getId).collect(Collectors.toSet());
        Map<Long, List<TaxonDto>> taxonMap = taxonLookupService.getForEntities(EntityType.PROVIDER_PROFILE, ids, locale);
        return profiles.stream()
                .map(p -> applyCategoryAndCityData(p, taxonMap.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    public ProviderProfileDto enrichWithCategoryAndCity(@NonNull ProviderProfileDto profile, @NonNull Locale locale) {
        List<TaxonDto> assigned = taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, profile.getId(), locale);
        return applyCategoryAndCityData(profile, assigned);
    }

    private static ProviderProfileDto applyCategoryAndCityData(ProviderProfileDto profile, List<TaxonDto> assigned) {
        CategoryAndCitySplit split = CategoryAndCitySplit.of(assigned);
        return profile.toBuilder()
                .categoryIds(split.categoryIds()).categoryNames(split.categoryNames())
                .cityTaxonId(split.cityTaxonId())
                .cityName(split.cityName())
                .build();
    }

    // ── Actor ─────────────────────────────────────────────────────────────────

    public List<ProviderProfileDto> enrichWithActorInfo(@NonNull List<ProviderProfileDto> profiles) {
        Set<Long> ids = profiles.stream().map(ProviderProfileDto::getActorId).collect(Collectors.toSet());
        Map<Long, UserDto> userMap = actorLookupService.findByIds(ids);
        return profiles.stream()
                .map(p -> applyActorData(p, userMap.get(p.getActorId())))
                .toList();
    }

    public ProviderProfileDto enrichWithActor(@NonNull ProviderProfileDto profile) {
        UserDto user = actorLookupService.findById(profile.getActorId()).orElse(null);
        return applyActorData(profile, user);
    }

    private static ProviderProfileDto applyActorData(ProviderProfileDto profile, UserDto user) {
        return profile.toBuilder()
                .actorName(user != null ? user.name() : null)
                .actorEmail(user != null ? user.email() : null)
                .build();
    }
}
