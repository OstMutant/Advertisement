package org.ost.advertisement.ui.views.header.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogEmailField;

import static org.ost.advertisement.constants.I18nKey.SIGNUP_EMAIL_LABEL;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class SignUpEmailField extends DialogEmailField {

    public SignUpEmailField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(SIGNUP_EMAIL_LABEL)
                .placeholderKey(SIGNUP_EMAIL_LABEL)
                .required(true)
                .build());
    }
}