package org.ost.advertisement.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvertisementEnrichmentService {

    private final ComponentFactory<AttachmentPort> attachmentPortFactory;
    private final ComponentFactory<TaxonPort>      taxonPortFactory;
    private final ComponentFactory<UserPort>       userPortFactory;

    // ── Category & city ──────────────────────────────────────────────────────

    public List<AdvertisementInfoDto> enrichWithCategoriesAndCity(@NonNull List<AdvertisementInfoDto> ads, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(taxonPort -> {
                    Set<Long> ids = ads.stream().map(AdvertisementInfoDto::getId).collect(Collectors.toSet());
                    Map<Long, List<TaxonDto>> taxonMap = taxonPort.getForEntities(EntityType.ADVERTISEMENT, ids, locale);
                    return ads.stream()
                            .map(ad -> applyCategoryAndCityData(ad, taxonMap.getOrDefault(ad.getId(), List.of())))
                            .toList();
                })
                .orElse(ads);
    }

    public AdvertisementInfoDto enrichWithCategoryAndCity(@NonNull AdvertisementInfoDto ad, @NonNull Locale locale) {
        return taxonPortFactory.findIfAvailable()
                .map(taxonPort -> applyCategoryAndCityData(ad, taxonPort.getForEntity(EntityType.ADVERTISEMENT, ad.getId(), locale)))
                .orElse(ad);
    }

    private static AdvertisementInfoDto applyCategoryAndCityData(AdvertisementInfoDto ad, List<TaxonDto> assigned) {
        Set<Long> catIds = assigned.stream()
                .filter(t -> t.getType() == TaxonType.CATEGORY)
                .map(TaxonDto::getId).collect(Collectors.toSet());
        List<String> catNames = assigned.stream()
                .filter(t -> t.getType() == TaxonType.CATEGORY)
                .map(TaxonDto::getName).toList();
        TaxonDto city = assigned.stream()
                .filter(t -> t.getType() == TaxonType.CITY)
                .findFirst().orElse(null);
        return ad.toBuilder()
                .categoryIds(catIds).categoryNames(catNames)
                .cityTaxonId(city != null ? city.getId() : null)
                .cityName(city != null ? city.getName() : null)
                .build();
    }

    // ── Actor ─────────────────────────────────────────────────────────────────

    public List<AdvertisementInfoDto> enrichWithActorInfo(@NonNull List<AdvertisementInfoDto> ads) {
        return userPortFactory.findIfAvailable()
                .map(userPort -> {
                    Set<Long> ids = ads.stream().map(AdvertisementInfoDto::getCreatedBy).collect(Collectors.toSet());
                    Map<Long, UserDto> userMap = userPort.findByIds(ids);
                    return ads.stream()
                            .map(ad -> applyActorData(ad, userMap.get(ad.getCreatedBy())))
                            .toList();
                })
                .orElse(ads);
    }

    public AdvertisementInfoDto enrichWithActor(@NonNull AdvertisementInfoDto ad) {
        return userPortFactory.findIfAvailable()
                .map(userPort -> applyActorData(ad, userPort.findById(ad.getCreatedBy()).orElse(null)))
                .orElse(ad);
    }

    private static AdvertisementInfoDto applyActorData(AdvertisementInfoDto ad, UserDto user) {
        return ad.toBuilder()
                .createdByUserName(user != null ? user.name() : null)
                .createdByUserEmail(user != null ? user.email() : null)
                .build();
    }

    // ── Media ─────────────────────────────────────────────────────────────────

    public List<AdvertisementInfoDto> enrichWithMediaSummary(@NonNull List<AdvertisementInfoDto> ads) {
        return attachmentPortFactory.findIfAvailable()
                .map(attachmentPort -> {
                    Set<Long> ids = ads.stream().map(AdvertisementInfoDto::getId).collect(Collectors.toSet());
                    Map<Long, AttachmentMediaSummaryDto> summaries = attachmentPort.getMediaSummaries(EntityType.ADVERTISEMENT, ids);
                    return ads.stream()
                            .map(ad -> applyMediaData(ad, summaries.getOrDefault(ad.getId(), AttachmentMediaSummaryDto.empty())))
                            .toList();
                })
                .orElse(ads);
    }

    public AdvertisementInfoDto enrichWithMedia(@NonNull AdvertisementInfoDto ad) {
        return attachmentPortFactory.findIfAvailable()
                .map(attachmentPort -> {
                    AttachmentMediaSummaryDto summary = attachmentPort.getMediaSummaries(EntityType.ADVERTISEMENT, Set.of(ad.getId()))
                            .getOrDefault(ad.getId(), AttachmentMediaSummaryDto.empty());
                    return applyMediaData(ad, summary);
                })
                .orElse(ad);
    }

    private static AdvertisementInfoDto applyMediaData(AdvertisementInfoDto ad, AttachmentMediaSummaryDto summary) {
        return ad.toBuilder()
                .mediaUrl(summary.displayUrl())
                .mediaContentType(summary.contentType())
                .mediaCount(summary.count())
                .build();
    }
}
