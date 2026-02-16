package org.ost.advertisement.ui.views.header.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPasswordField;

import static org.ost.advertisement.constants.I18nKey.SIGNUP_PASSWORD_LABEL;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class SignUpPasswordField extends DialogPasswordField {

    public SignUpPasswordField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(SIGNUP_PASSWORD_LABEL)
                .placeholderKey(SIGNUP_PASSWORD_LABEL)
                .required(true)
                .build());
    }
}