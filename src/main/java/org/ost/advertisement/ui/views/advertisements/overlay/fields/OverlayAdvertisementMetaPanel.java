package org.ost.advertisement.ui.views.advertisements.overlay.fields;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.time.Instant;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class OverlayAdvertisementMetaPanel extends HorizontalLayout {

    private final transient I18nService i18n;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String  authorName;
        @NonNull Instant createdAt;
        Instant          updatedAt;
    }

    private OverlayAdvertisementMetaPanel configure(Parameters p) {
        addClassName("overlay__meta-container");
        addClassName("advertisement-meta");

        Span authorSpan = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_OVERLAY_AUTHOR) + " " + p.getAuthorName());
        authorSpan.addClassName("advertisement-meta-author");

        Span createdSpan = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_OVERLAY_CREATED) + " " + TimeZoneUtil.formatInstantHuman(p.getCreatedAt()));
        createdSpan.addClassName("advertisement-meta-date");

        boolean wasEdited = p.getUpdatedAt() != null && !p.getUpdatedAt().equals(p.getCreatedAt());
        if (wasEdited) {
            Span updatedSpan = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_OVERLAY_UPDATED) + " " + TimeZoneUtil.formatInstantHuman(p.getUpdatedAt()));
            updatedSpan.addClassName("advertisement-meta-date");
            add(authorSpan, separator(), createdSpan, separator(), updatedSpan);
        } else {
            add(authorSpan, separator(), createdSpan);
        }

        return this;
    }

    private static Span separator() {
        Span sep = new Span(" · ");
        sep.addClassName("advertisement-meta-separator");
        return sep;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<OverlayAdvertisementMetaPanel> provider;

        public OverlayAdvertisementMetaPanel build(AdvertisementInfoDto ad) {
            return provider.getObject().configure(
                    Parameters.builder()
                            .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                            .createdAt(ad.getCreatedAt())
                            .updatedAt(ad.getUpdatedAt())
                            .build()
            );
        }
    }
}