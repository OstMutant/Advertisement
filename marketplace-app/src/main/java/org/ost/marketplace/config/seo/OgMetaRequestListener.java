package org.ost.marketplace.config.seo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.communication.IndexHtmlRequestListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.ost.marketplace.ui.views.utils.HtmlExcerptUtil;
import org.ost.orchestrator.services.AdvertisementDisplayEnrichmentService;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OgMetaRequestListener implements VaadinServiceInitListener, IndexHtmlRequestListener {

    private static final Pattern AD_PATH = Pattern.compile("^/ads/(\\d+)$");
    private static final Pattern PROVIDER_PATH = Pattern.compile("^/providers/(\\d+)$");
    private static final int EXCERPT_MAX_LENGTH = 160;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AdvertisementReadService               advertisementReadService;
    private final AdvertisementDisplayEnrichmentService  enrichmentService;
    private final ProviderProfileReadService              providerProfileReadService;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private final Cache<Long, Optional<AdvertisementInfoDto>> ogCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(500)
            .build();

    private final Cache<Long, Optional<ProviderProfileDto>> providerOgCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(500)
            .build();

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addIndexHtmlRequestListener(this);
    }

    @Override
    public void modifyIndexHtmlResponse(IndexHtmlResponse response) {
        VaadinRequest request = response.getVaadinRequest();
        String path = request.getPathInfo();
        if (path == null) return;

        Matcher adMatcher = AD_PATH.matcher(path);
        if (adMatcher.matches()) {
            Long adId = Long.valueOf(adMatcher.group(1));
            ogCache.get(adId, id -> advertisementReadService.findById(id)
                            .map(enrichmentService::enrichWithMedia))
                    .ifPresent(ad -> injectAdvertisementMeta(response.getDocument(), ad, path));
            return;
        }

        Matcher providerMatcher = PROVIDER_PATH.matcher(path);
        if (providerMatcher.matches()) {
            Long providerId = Long.valueOf(providerMatcher.group(1));
            providerOgCache.get(providerId, providerProfileReadService::findById)
                    .ifPresent(profile -> injectProviderProfileMeta(response.getDocument(), profile, path));
        }
    }

    private void injectAdvertisementMeta(Document document, AdvertisementInfoDto ad, String path) {
        Element head = document.head();
        String description = excerpt(HtmlExcerptUtil.plainText(ad.getDescription()));
        String url = publicBaseUrl + path;
        String imageUrl = versionedImageUrl(ad);

        addMeta(head, "og:title", ad.getTitle());
        addMeta(head, "og:description", description);
        addMeta(head, "og:url", url);
        addMeta(head, "og:type", "product");
        addTwitterMeta(head, "twitter:card", "summary_large_image");
        if (imageUrl != null) {
            addMeta(head, "og:image", imageUrl);
        }
        addAdvertisementJsonLd(head, ad, url, description, imageUrl);
    }

    private void injectProviderProfileMeta(Document document, ProviderProfileDto profile, String path) {
        Element head = document.head();
        String title = profile.getActorName() != null ? profile.getActorName() : profile.getKind().name();
        String description = excerpt(HtmlExcerptUtil.plainText(profile.getAbout()));
        String url = publicBaseUrl + path;

        addMeta(head, "og:title", title);
        addMeta(head, "og:description", description);
        addMeta(head, "og:url", url);
        addMeta(head, "og:type", "profile");
        addTwitterMeta(head, "twitter:card", "summary_large_image");
        addProviderProfileJsonLd(head, title, url, description);
    }

    private static String versionedImageUrl(AdvertisementInfoDto ad) {
        if (ad.getMediaUrl() == null) return null;
        String separator = ad.getMediaUrl().contains("?") ? "&" : "?";
        return ad.getMediaUrl() + separator + "v=" + ad.getUpdatedAt().getEpochSecond();
    }

    private static void addMeta(Element head, String property, String content) {
        head.appendElement("meta")
                .attr("property", property)
                .attr("content", content);
    }

    private static void addTwitterMeta(Element head, String name, String content) {
        head.appendElement("meta")
                .attr("name", name)
                .attr("content", content);
    }

    private static void addAdvertisementJsonLd(Element head, AdvertisementInfoDto ad, String url, String description, String imageUrl) {
        Map<String, Object> jsonLd = new LinkedHashMap<>();
        jsonLd.put("@context", "https://schema.org");
        jsonLd.put("@type", "Product");
        jsonLd.put("name", ad.getTitle());
        jsonLd.put("description", description);
        jsonLd.put("url", url);
        if (imageUrl != null) {
            jsonLd.put("image", imageUrl);
        }
        appendJsonLdScript(head, jsonLd);
    }

    private static void addProviderProfileJsonLd(Element head, String title, String url, String description) {
        Map<String, Object> jsonLd = new LinkedHashMap<>();
        jsonLd.put("@context", "https://schema.org");
        jsonLd.put("@type", "ProfilePage");
        jsonLd.put("name", title);
        jsonLd.put("description", description);
        jsonLd.put("url", url);
        appendJsonLdScript(head, jsonLd);
    }

    private static void appendJsonLdScript(Element head, Map<String, Object> jsonLd) {
        String json = toJson(jsonLd);
        head.appendElement("script")
                .attr("type", "application/ld+json")
                .appendChild(new DataNode(json));
    }

    @SneakyThrows
    private static String toJson(Map<String, Object> value) {
        return JSON.writeValueAsString(value);
    }

    private static String excerpt(String text) {
        if (text.length() <= EXCERPT_MAX_LENGTH) return text;
        return text.substring(0, EXCERPT_MAX_LENGTH).stripTrailing() + "…";
    }
}
