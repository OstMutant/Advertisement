package org.ost.advertisement.ui.views.main.tabs.advertisements.overlay.elements;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.utils.TimeZoneUtil;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.springframework.context.annotation.Scope;

import java.time.Instant;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class OverlayAdvertisementMetaPanel extends HorizontalLayout
        implements Configurable<OverlayAdvertisementMetaPanel, OverlayAdvertisementMetaPanel.Parameters>, I18nParams, Initialization<OverlayAdvertisementMetaPanel> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String  authorName;
        @NonNull Instant createdAt;
        Instant          updatedAt;

        public static Parameters from(AdvertisementInfoDto ad) {
            return Parameters.builder()
                    .authorName(ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—")
                    .createdAt(ad.getCreatedAt())
                    .updatedAt(ad.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public OverlayAdvertisementMetaPanel init() {
        addClassName("overlay__meta-container");
        addClassName("advertisement-meta");
        return this;
    }

    @Override
    public OverlayAdvertisementMetaPanel configure(Parameters p) {
        Span authorSpan = new Span(getValue(ADVERTISEMENT_DESCRIPTION_OVERLAY_AUTHOR) + " " + p.getAuthorName());
        authorSpan.addClassName("advertisement-meta-author");

        Span createdSpan = new Span(getValue(ADVERTISEMENT_DESCRIPTION_OVERLAY_CREATED) + " " + TimeZoneUtil.formatInstantHuman(p.getCreatedAt()));
        createdSpan.addClassName("advertisement-meta-date");

        boolean wasEdited = p.getUpdatedAt() != null && !p.getUpdatedAt().equals(p.getCreatedAt());
        if (wasEdited) {
            Span updatedSpan = new Span(getValue(ADVERTISEMENT_DESCRIPTION_OVERLAY_UPDATED) + " " + TimeZoneUtil.formatInstantHuman(p.getUpdatedAt()));
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
}
