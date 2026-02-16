package org.ost.advertisement.ui.views.users.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.LabeledField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_ID_LABEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogUserIdLabeledField extends LabeledField {

    public DialogUserIdLabeledField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(USER_DIALOG_FIELD_ID_LABEL)
                .cssClass("base-label")
                .cssClass("email-label")
                .build());
    }
}