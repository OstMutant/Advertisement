package org.ost.advertisement.ui.views.advertisements.card;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.ui.utils.TimeZoneUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.time.Instant;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementCardMetaPanel extends Span {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull String authorName;
        String authorEmail;
        @NonNull String dateLabel;
        @NonNull Instant date;
    }

    private AdvertisementCardMetaPanel configure(Parameters p) {
        Span authorSpan = new Span(p.getAuthorName());
        authorSpan.addClassName("advertisement-meta-author");
        if (p.getAuthorEmail() != null) {
            authorSpan.getElement().setAttribute("title", p.getAuthorEmail());
        }

        Span separator = new Span(" · ");
        separator.addClassName("advertisement-meta-separator");

        Span dateSpan = new Span(p.getDateLabel() + " " + TimeZoneUtil.formatInstantHuman(p.getDate()));
        dateSpan.addClassName("advertisement-meta-date");

        addClassName("advertisement-meta");
        add(authorSpan, separator, dateSpan);
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<AdvertisementCardMetaPanel> provider;

        public AdvertisementCardMetaPanel build(Parameters p) {
            return provider.getObject().configure(p);
        }
    }
}