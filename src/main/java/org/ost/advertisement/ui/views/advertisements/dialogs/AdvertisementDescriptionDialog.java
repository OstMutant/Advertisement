package org.ost.advertisement.ui.views.advertisements.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.advertisement.services.I18nService;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_DESCRIPTION_DIALOG_CLOSE;

public class AdvertisementDescriptionDialog extends Dialog {

    public AdvertisementDescriptionDialog(I18nService i18n, String title, String description) {
        initDialog(title);

        Span content = createContent(description);
        Button closeButton = createCloseButton(i18n);

        VerticalLayout layout = new VerticalLayout(content, closeButton);
        layout.addClassName("advertisement-description-layout");

        add(layout);
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

    private Button createCloseButton(I18nService i18n) {
        Button close = new Button(i18n.get(ADVERTISEMENT_DESCRIPTION_DIALOG_CLOSE), _ -> close());
        close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        close.addClassName("advertisement-description-close");
        return close;
    }
}