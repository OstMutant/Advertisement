package org.ost.advertisement.ui.views.header.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.fields.PasswordField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.LOGIN_PASSWORD_LABEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class LoginPasswordField extends PasswordField {

    public LoginPasswordField(I18nService i18nService) {
        super(i18nService);
        configure(Parameters.builder()
                .labelKey(LOGIN_PASSWORD_LABEL)
                .placeholderKey(LOGIN_PASSWORD_LABEL)
                .required(true)
                .build());
    }
}
