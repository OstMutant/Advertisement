package org.ost.orchestrator.advertisement.enrich;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.shared.ActorLookupService;
import org.ost.orchestrator.shared.TaxonLookupService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles the display-only fields of {@link AdvertisementInfoDto} (category/city names, author
 * name/email, media summary) from the Advertisement, Taxon, User, and Attachment ports.
 */
@Service
@RequiredArgsConstructor
public class AdvertisementDisplayEnrichmentService {

    private final TaxonLookupService                taxonLookupService;
    private final ActorLookupService                 actorLookupService;
    private final ComponentFactory<AttachmentPort>  attachmentPortFactory;

    // ── Category & city ──────────────────────────────────────────────────────

    public List<AdvertisementInfoDto> enrichWithCategoriesAndCity(@NonNull List<AdvertisementInfoDto> ads, @NonNull Locale locale) {
        Set<Long> ids = ads.stream().map(AdvertisementInfoDto::getId).collect(Collectors.toSet());
        Map<Long, List<TaxonDto>> taxonMap = taxonLookupService.getForEntities(EntityType.ADVERTISEMENT, ids, locale);
        return ads.stream()
                .map(ad -> applyCategoryAndCityData(ad, taxonMap.getOrDefault(ad.getId(), List.of())))
                .toList();
    }

    public AdvertisementInfoDto enrichWithCategoryAndCity(@NonNull AdvertisementInfoDto ad, @NonNull Locale locale) {
        List<TaxonDto> assigned = taxonLookupService.getForEntity(EntityType.ADVERTISEMENT, ad.getId(), locale);
        return applyCategoryAndCityData(ad, assigned);
    }

    private static AdvertisementInfoDto applyCategoryAndCityData(AdvertisementInfoDto ad, List<TaxonDto> assigned) {
        Set<Long> catIds = new LinkedHashSet<>();
        List<String> catNames = new ArrayList<>();
        TaxonDto city = null;
        for (TaxonDto t : assigned) {
            if (t.getType() == TaxonType.CATEGORY) {
                catIds.add(t.getId());
                catNames.add(t.getName());
            } else if (t.getType() == TaxonType.CITY && city == null) {
                city = t;
            }
        }
        return ad.toBuilder()
                .categoryIds(catIds).categoryNames(catNames)
                .cityTaxonId(city != null ? city.getId() : null)
                .cityName(city != null ? city.getName() : null)
                .build();
    }

    // ── Actor ─────────────────────────────────────────────────────────────────

    public List<AdvertisementInfoDto> enrichWithActorInfo(@NonNull List<AdvertisementInfoDto> ads) {
        Set<Long> ids = ads.stream().map(AdvertisementInfoDto::getCreatedBy).collect(Collectors.toSet());
        Map<Long, UserDto> userMap = actorLookupService.findByIds(ids);
        return ads.stream()
                .map(ad -> applyActorData(ad, userMap.get(ad.getCreatedBy())))
                .toList();
    }

    public AdvertisementInfoDto enrichWithActor(@NonNull AdvertisementInfoDto ad) {
        UserDto user = actorLookupService.findById(ad.getCreatedBy()).orElse(null);
        return applyActorData(ad, user);
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

    // ── Convenience ──────────────────────────────────────────────────────────

    /** Applies category/city, actor, and media enrichment to a single advertisement, in order. */
    public AdvertisementInfoDto enrichSingle(@NonNull AdvertisementInfoDto ad, @NonNull Locale locale) {
        AdvertisementInfoDto enriched = enrichWithCategoryAndCity(ad, locale);
        enriched = enrichWithActor(enriched);
        return enrichWithMedia(enriched);
    }
}
