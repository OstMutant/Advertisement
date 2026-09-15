package org.ost.orchestrator.services;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/** Builds and caches {@code /sitemap.xml}'s XML body; {@link #invalidate()} is called by the save-side services whenever an advertisement or provider profile changes, so the 15-minute cache never serves a snapshot older than the last real write. */
@Service
@RequiredArgsConstructor
public class SitemapService {

    private static final int  PAGE_SIZE        = 500;
    private static final Sort SORT_BY_AD_ID       = Sort.by(AdvertisementInfoDto.Fields.id).ascending();
    private static final Sort SORT_BY_PROVIDER_ID = Sort.by(ProviderProfileDto.Fields.id).ascending();

    private final AdvertisementReadService   advertisementReadService;
    private final ProviderProfileReadService providerProfileReadService;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private final Cache<String, String> sitemapCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(1)
            .build();

    public String getSitemap() {
        return sitemapCache.get("sitemap", _ -> buildSitemap());
    }

    public void invalidate() {
        sitemapCache.invalidateAll();
    }

    private String buildSitemap() {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        allAdvertisements().forEach(ad -> xml
                .append("  <url><loc>").append(advertisementUrl(ad.getId())).append("</loc>")
                .append("<lastmod>").append(DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(ad.getUpdatedAt()))
                .append("</lastmod></url>\n"));

        allProviderProfiles().forEach(profile -> xml
                .append("  <url><loc>").append(providerProfileUrl(profile.getId())).append("</loc>")
                .append("<lastmod>").append(DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(profile.getUpdatedAt()))
                .append("</lastmod></url>\n"));

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private String advertisementUrl(Long adId) {
        return publicBaseUrl + "/ads/" + adId;
    }

    private String providerProfileUrl(Long id) {
        return publicBaseUrl + "/providers/" + id;
    }

    private Stream<AdvertisementInfoDto> allAdvertisements() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        return Stream.iterate(0, page -> page + 1)
                .map(page -> advertisementReadService.getFiltered(filter, page, PAGE_SIZE, SORT_BY_AD_ID))
                .takeWhile(page -> !page.isEmpty())
                .flatMap(List::stream);
    }

    private Stream<ProviderProfileDto> allProviderProfiles() {
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        return Stream.iterate(0, page -> page + 1)
                .map(page -> providerProfileReadService.getFiltered(filter, page, PAGE_SIZE, SORT_BY_PROVIDER_ID))
                .takeWhile(page -> !page.isEmpty())
                .flatMap(List::stream);
    }
}
