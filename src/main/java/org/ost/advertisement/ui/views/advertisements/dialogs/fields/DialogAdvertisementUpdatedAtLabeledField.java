package org.ost.advertisement.ui.views.advertisements.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.LabeledField;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_DIALOG_FIELD_UPDATED;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class DialogAdvertisementUpdatedAtLabeledField extends LabeledField {

    public DialogAdvertisementUpdatedAtLabeledField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(ADVERTISEMENT_DIALOG_FIELD_UPDATED)
                .cssClass("base-label")
                .cssClass("gray-label")
                .build());
    }
}