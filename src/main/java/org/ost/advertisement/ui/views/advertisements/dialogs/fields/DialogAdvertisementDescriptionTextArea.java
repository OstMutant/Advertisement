package org.ost.advertisement.ui.views.advertisements.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTextArea;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class DialogAdvertisementDescriptionTextArea extends DialogTextArea {

    public DialogAdvertisementDescriptionTextArea(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION)
                .placeholderKey(ADVERTISEMENT_DIALOG_FIELD_DESCRIPTION)
                .maxLength(1000)
                .required(true)
                .build());
    }
}
