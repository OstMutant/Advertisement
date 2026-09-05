package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        Map<Long, List<TaxonDto>> categoryMap = taxonLookupService.getForEntities(EntityType.PROVIDER_PROFILE, ids, locale);
        Map<Long, TaxonDto> cityMap = findCities(profiles, locale);
        return profiles.stream()
                .map(p -> applyCategoryAndCityData(p, categoryMap.getOrDefault(p.getId(), List.of()),
                        p.getCityTaxonId() != null ? cityMap.get(p.getCityTaxonId()) : null))
                .toList();
    }

    public ProviderProfileDto enrichWithCategoryAndCity(@NonNull ProviderProfileDto profile, @NonNull Locale locale) {
        List<TaxonDto> categories = taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, profile.getId(), locale);
        TaxonDto city = profile.getCityTaxonId() != null
                ? taxonLookupService.findById(profile.getCityTaxonId(), locale).orElse(null)
                : null;
        return applyCategoryAndCityData(profile, categories, city);
    }

    private Map<Long, TaxonDto> findCities(List<ProviderProfileDto> profiles, Locale locale) {
        Set<Long> cityIds = profiles.stream()
                .map(ProviderProfileDto::getCityTaxonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return taxonLookupService.findByIds(cityIds, locale);
    }

    private static ProviderProfileDto applyCategoryAndCityData(ProviderProfileDto profile, List<TaxonDto> categories, TaxonDto city) {
        Set<Long> catIds = new LinkedHashSet<>();
        List<String> catNames = new ArrayList<>();
        for (TaxonDto t : categories) {
            if (t.getType() == TaxonType.CATEGORY) {
                catIds.add(t.getId());
                catNames.add(t.getName());
            }
        }
        return profile.toBuilder()
                .categoryIds(catIds).categoryNames(catNames)
                .cityName(city != null ? city.getName() : null)
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
