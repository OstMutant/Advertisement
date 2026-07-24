package org.ost.marketplace.config.seo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.communication.IndexHtmlRequestListener;
import com.vaadin.flow.server.communication.IndexHtmlResponse;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.ost.marketplace.ui.views.utils.HtmlExcerptUtil;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class OgMetaRequestListener implements VaadinServiceInitListener, IndexHtmlRequestListener {

    private static final Pattern AD_PATH = Pattern.compile("^/ads/(\\d+)$");
    private static final int EXCERPT_MAX_LENGTH = 160;

    private final ComponentFactory<AdvertisementPort> advertisementPortFactory;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    private final Cache<Long, Optional<AdvertisementInfoDto>> ogCache = Caffeine.newBuilder()
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

        Matcher matcher = AD_PATH.matcher(path);
        if (!matcher.matches()) return;

        Long adId = Long.valueOf(matcher.group(1));
        ogCache.get(adId, id -> advertisementPortFactory.findIfAvailable().flatMap(p -> p.findById(id)))
                .ifPresent(ad -> injectMeta(response.getDocument(), ad, path));
    }

    private void injectMeta(Document document, AdvertisementInfoDto ad, String path) {
        Element head = document.head();
        String description = excerpt(HtmlExcerptUtil.plainText(ad.getDescription()));
        String url = publicBaseUrl + path;

        addMeta(head, "og:title", ad.getTitle());
        addMeta(head, "og:description", description);
        addMeta(head, "og:url", url);
        addMeta(head, "og:type", "product");
        addMeta(head, "twitter:card", "summary_large_image");
        if (ad.getMediaUrl() != null) {
            addMeta(head, "og:image", ad.getMediaUrl());
        }
    }

    private static void addMeta(Element head, String property, String content) {
        head.appendElement("meta")
                .attr("property", property)
                .attr("content", content);
    }

    private static String excerpt(String text) {
        if (text.length() <= EXCERPT_MAX_LENGTH) return text;
        return text.substring(0, EXCERPT_MAX_LENGTH).stripTrailing() + "…";
    }
}
