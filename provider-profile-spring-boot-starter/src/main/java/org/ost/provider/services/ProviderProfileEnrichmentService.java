package org.ost.provider.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderProfileEnrichmentService {

    private final ComponentFactory<TaxonPort> taxonPortFactory;
    private final ComponentFactory<UserPort>  userPortFactory;

    // ── Categories & city ────────────────────────────────────────────────────

    public List<ProviderProfileDto> enrichWithCategoriesAndCity(@NonNull List<ProviderProfileDto> profiles, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(taxonPort -> {
                    Set<Long> ids = profiles.stream().map(ProviderProfileDto::getId).collect(Collectors.toSet());
                    Map<Long, List<TaxonDto>> categoryMap = taxonPort.getForEntities(EntityType.PROVIDER_PROFILE, ids, locale);
                    Map<Long, TaxonDto> cityMap = findCities(taxonPort, profiles, locale);
                    return profiles.stream()
                            .map(p -> applyCategoryAndCityData(p, categoryMap.getOrDefault(p.getId(), List.of()), cityMap.get(p.getCityTaxonId())))
                            .toList();
                })
                .orElse(profiles);
    }

    public ProviderProfileDto enrichWithCategoryAndCity(@NonNull ProviderProfileDto profile, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(taxonPort -> {
                    List<TaxonDto> categories = taxonPort.getForEntity(EntityType.PROVIDER_PROFILE, profile.getId(), locale);
                    TaxonDto city = profile.getCityTaxonId() != null
                            ? taxonPort.findById(profile.getCityTaxonId(), locale).orElse(null)
                            : null;
                    return applyCategoryAndCityData(profile, categories, city);
                })
                .orElse(profile);
    }

    private static Map<Long, TaxonDto> findCities(TaxonPort taxonPort, List<ProviderProfileDto> profiles, Locale locale) {
        Set<Long> cityIds = profiles.stream()
                .map(ProviderProfileDto::getCityTaxonId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return taxonPort.findByIds(cityIds, locale);
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
        return userPortFactory.findIfAvailable()
                .map(userPort -> {
                    Set<Long> ids = profiles.stream().map(ProviderProfileDto::getActorId).collect(Collectors.toSet());
                    Map<Long, UserDto> userMap = userPort.findByIds(ids);
                    return profiles.stream()
                            .map(p -> applyActorData(p, userMap.get(p.getActorId())))
                            .toList();
                })
                .orElse(profiles);
    }

    public ProviderProfileDto enrichWithActor(@NonNull ProviderProfileDto profile) {
        return userPortFactory.findIfAvailable()
                .map(userPort -> applyActorData(profile, userPort.findById(profile.getActorId()).orElse(null)))
                .orElse(profile);
    }

    private static ProviderProfileDto applyActorData(ProviderProfileDto profile, UserDto user) {
        return profile.toBuilder()
                .actorName(user != null ? user.name() : null)
                .actorEmail(user != null ? user.email() : null)
                .build();
    }
}
