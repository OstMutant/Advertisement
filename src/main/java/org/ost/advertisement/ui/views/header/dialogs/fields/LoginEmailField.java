package org.ost.advertisement.ui.views.header.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogEmailField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.LOGIN_EMAIL_LABEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class LoginEmailField extends DialogEmailField {

    public LoginEmailField(I18nService i18n) {
        super(Parameters.builder()
                .i18n(i18n)
                .labelKey(LOGIN_EMAIL_LABEL)
                .placeholderKey(LOGIN_EMAIL_LABEL)
                .required(true)
                .build());
    }
}