package org.ost.advertisement.ui.views.advertisements.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_DIALOG_BUTTON_CANCEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogAdvertisementCancelButton extends DialogTertiaryButton {

    public DialogAdvertisementCancelButton(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(ADVERTISEMENT_DIALOG_BUTTON_CANCEL)
                .build());
    }
}
