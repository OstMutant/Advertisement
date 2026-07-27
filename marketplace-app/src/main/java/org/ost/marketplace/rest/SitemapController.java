package org.ost.marketplace.rest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.ui.views.services.AppLinkService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    private static final int PAGE_SIZE = 500;
    private static final Sort SORT_BY_ID = Sort.by(AdvertisementInfoDto.Fields.id).ascending();

    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;
    private final AppLinkService appLinkService;

    private final Cache<String, String> sitemapCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(1)
            .build();

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return sitemapCache.get("sitemap", _ -> buildSitemap());
    }

    private String buildSitemap() {
        StringBuilder xml = new StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        advertisementPortFactory.findIfAvailable().ifPresent(port -> allAdvertisements(port).forEach(ad -> xml
                .append("  <url><loc>").append(appLinkService.advertisementUrl(ad.getId())).append("</loc>")
                .append("<lastmod>").append(DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC).format(ad.getUpdatedAt()))
                .append("</lastmod></url>\n")));

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private Stream<AdvertisementInfoDto> allAdvertisements(AdvertisementPort port) {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        return Stream.iterate(0, page -> page + 1)
                .map(page -> port.getFiltered(filter, page, PAGE_SIZE, SORT_BY_ID, Locale.ENGLISH))
                .takeWhile(page -> !page.isEmpty())
                .flatMap(List::stream);
    }
}
