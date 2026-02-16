package org.ost.advertisement.ui.views.users.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.LabeledField;

import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_UPDATED_LABEL;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class DialogUserUpdatedAtLabeledField extends LabeledField {

    public DialogUserUpdatedAtLabeledField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(USER_DIALOG_FIELD_UPDATED_LABEL)
                .cssClass("base-label")
                .cssClass("gray-label")
                .build());
    }
}