package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.TimeZoneUtil;

import static org.ost.advertisement.constants.I18nKey.*;

public class AdvertisementDescriptionDialog extends Dialog {

    public AdvertisementDescriptionDialog(I18nService i18n, AdvertisementInfoDto ad) {
        initDialog(ad.getTitle());

        Span content = createContent(ad.getDescription());
        Span meta = createMeta(i18n, ad);
        Button closeButton = createCloseButton(i18n);

        VerticalLayout body = new VerticalLayout(content);
        body.addClassName("advertisement-description-body");
        body.setPadding(false);
        body.setSpacing(false);

        VerticalLayout layout = new VerticalLayout(body, meta);
        layout.addClassName("advertisement-description-layout");

        add(layout);
        getFooter().add(closeButton);
    }

    private void initDialog(String title) {
        setHeaderTitle(title);
        setCloseOnEsc(true);
        setCloseOnOutsideClick(true);
        addClassName("advertisement-description-dialog");
    }

    private Span createContent(String description) {
        Span content = new Span(description);
        content.addClassName("advertisement-description-content");
        return content;
    }

    private Span createMeta(I18nService i18n, AdvertisementInfoDto ad) {
        String author = ad.getCreatedByUserName() != null ? ad.getCreatedByUserName() : "—";
        String created = TimeZoneUtil.formatInstantHuman(ad.getCreatedAt());

        boolean wasEdited = ad.getUpdatedAt() != null && !ad.getUpdatedAt().equals(ad.getCreatedAt());
        String datePart = wasEdited
                ? i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CREATED) + " " + created
                  + "  ·  " + i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_UPDATED) + " "
                  + TimeZoneUtil.formatInstantHuman(ad.getUpdatedAt())
                : i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CREATED) + " " + created;

        Span meta = new Span(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_AUTHOR) + " " + author + "  ·  " + datePart);
        meta.addClassName("advertisement-description-meta");
        return meta;
    }

    private Button createCloseButton(I18nService i18n) {
        Button close = new Button(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CLOSE), _ -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        close.addClassName("advertisement-description-close");
        return close;
    }
}
