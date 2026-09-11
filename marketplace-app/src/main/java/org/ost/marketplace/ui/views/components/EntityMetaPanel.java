package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.query.utils.TimeZoneUtil;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.ost.marketplace.services.i18n.I18nKey.ENTITY_META_AUTHOR;
import static org.ost.marketplace.services.i18n.I18nKey.ENTITY_META_CREATED;
import static org.ost.marketplace.services.i18n.I18nKey.ENTITY_META_UPDATED;

/**
 * Author + created/updated meta line shared by every card and detail-view surface, where a null
 * {@code authorName} omits the author span and {@link Variant} selects card (one collapsed date)
 * vs overlay (created plus updated-if-edited) layout.
 */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class EntityMetaPanel extends Div
        implements Configurable<EntityMetaPanel, EntityMetaPanel.Parameters>, I18nParams, Initialization<EntityMetaPanel> {

    public enum Variant { CARD, OVERLAY }

    @Value
    @lombok.Builder
    public static class Parameters {
        String           authorName;
        String           authorEmail;
        @NonNull Instant createdAt;
        @NonNull Instant updatedAt;
        @NonNull Variant variant;

        /** Card-variant line with an author (Advertisement). */
        public static Parameters card(String authorName, String authorEmail, Instant createdAt, Instant updatedAt) {
            return of(Variant.CARD, authorName, authorEmail, createdAt, updatedAt);
        }

        /** Card-variant line with no author (Provider Profile). */
        public static Parameters card(Instant createdAt, Instant updatedAt) {
            return of(Variant.CARD, null, null, createdAt, updatedAt);
        }

        /** Overlay-variant line with an author (Advertisement). */
        public static Parameters overlay(String authorName, String authorEmail, Instant createdAt, Instant updatedAt) {
            return of(Variant.OVERLAY, authorName, authorEmail, createdAt, updatedAt);
        }

        /** Overlay-variant line with no author (Provider Profile). */
        public static Parameters overlay(Instant createdAt, Instant updatedAt) {
            return of(Variant.OVERLAY, null, null, createdAt, updatedAt);
        }

        private static Parameters of(Variant variant, String authorName, String authorEmail, Instant createdAt, Instant updatedAt) {
            return builder().variant(variant).authorName(authorName).authorEmail(authorEmail)
                    .createdAt(createdAt).updatedAt(updatedAt).build();
        }
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public EntityMetaPanel init() {
        addClassName("entity-meta");
        return this;
    }

    @Override
    public EntityMetaPanel configure(Parameters p) {
        addClassName(p.getVariant() == Variant.CARD ? "entity-meta--card" : "entity-meta--overlay");
        boolean edited = !p.getUpdatedAt().equals(p.getCreatedAt());

        List<Component> parts = new ArrayList<>();
        if (p.getAuthorName() != null) {
            parts.add(authorSpan(p));
        }
        if (p.getVariant() == Variant.CARD) {
            parts.add(dateSpan(edited ? ENTITY_META_UPDATED : ENTITY_META_CREATED,
                    edited ? p.getUpdatedAt() : p.getCreatedAt()));
        } else {
            parts.add(dateSpan(ENTITY_META_CREATED, p.getCreatedAt()));
            if (edited) {
                parts.add(dateSpan(ENTITY_META_UPDATED, p.getUpdatedAt()));
            }
        }

        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) add(separator());
            add(parts.get(i));
        }
        return this;
    }

    private Span authorSpan(Parameters p) {
        Span author = new Span(getValue(ENTITY_META_AUTHOR) + " " + p.getAuthorName());
        author.addClassName("entity-meta-author");
        if (p.getAuthorEmail() != null) {
            author.getElement().setAttribute("title", p.getAuthorEmail());
        }
        return author;
    }

    private Span dateSpan(I18nKey label, Instant when) {
        Span date = new Span(getValue(label) + " " + TimeZoneUtil.formatInstantHuman(when));
        date.addClassName("entity-meta-date");
        return date;
    }

    private static Span separator() {
        Span sep = new Span("·");
        sep.addClassName("entity-meta-separator");
        return sep;
    }
}
