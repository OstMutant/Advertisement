package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.html.Span;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

import java.time.Instant;

import static org.ost.advertisement.constants.I18nKey.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdvertisementMetaFactory {

    /**
     * Будує мета-рядок:
     * "Автор: X · Створено: date"                       — якщо не редагувався
     * "Автор: X · Створено: date · Оновлено: date"      — якщо редагувався
     */
    public static Span create(I18nService i18n, String authorName, Instant createdAt, Instant updatedAt) {
        String author = authorName != null ? authorName : "—";
        boolean wasEdited = updatedAt != null && !updatedAt.equals(createdAt);

        Span authorSpan = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_AUTHOR) + " " + author);
        authorSpan.addClassName("advertisement-meta-author");

        Span sep1 = separator();
        Span createdSpan = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CREATED) + " " + TimeZoneUtil.formatInstantHuman(createdAt));
        createdSpan.addClassName("advertisement-meta-date");

        Span meta;
        if (wasEdited) {
            Span sep2 = separator();
            Span updatedSpan = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_UPDATED) + " " + TimeZoneUtil.formatInstantHuman(updatedAt));
            updatedSpan.addClassName("advertisement-meta-date");
            meta = new Span(authorSpan, sep1, createdSpan, sep2, updatedSpan);
        } else {
            meta = new Span(authorSpan, sep1, createdSpan);
        }

        meta.addClassName("advertisement-meta");
        return meta;
    }

    private static Span separator() {
        Span sep = new Span(" · ");
        sep.addClassName("advertisement-meta-separator");
        return sep;
    }
}