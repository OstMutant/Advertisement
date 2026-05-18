package org.ost.marketplace.ui.views.main.tabs.advertisements.card;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.ui.views.utils.TimeZoneUtil;
import org.ost.platform.core.ui.ComponentBuilder;
import org.ost.platform.core.ui.Configurable;
import org.ost.platform.core.ui.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.time.Instant;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementCardMetaPanel extends Span
        implements Configurable<AdvertisementCardMetaPanel, AdvertisementCardMetaPanel.Parameters>, Initialization<AdvertisementCardMetaPanel> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String  authorName;
        String           authorEmail;
        @NonNull String  dateLabel;
        @NonNull Instant date;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<AdvertisementCardMetaPanel, Parameters> {
        @Getter
        private final ObjectProvider<AdvertisementCardMetaPanel> provider;
    }

    @Override
    @PostConstruct
    public AdvertisementCardMetaPanel init() {
        addClassName("advertisement-meta");
        return this;
    }

    @Override
    public AdvertisementCardMetaPanel configure(Parameters p) {
        Span authorSpan = new Span(p.getAuthorName());
        authorSpan.addClassName("advertisement-meta-author");
        if (p.getAuthorEmail() != null) {
            authorSpan.getElement().setAttribute("title", p.getAuthorEmail());
        }

        Span separator = new Span(" · ");
        separator.addClassName("advertisement-meta-separator");

        Span dateSpan = new Span(p.getDateLabel() + " " + TimeZoneUtil.formatInstantHuman(p.getDate()));
        dateSpan.addClassName("advertisement-meta-date");

        add(authorSpan, separator, dateSpan);
        return this;
    }
}
