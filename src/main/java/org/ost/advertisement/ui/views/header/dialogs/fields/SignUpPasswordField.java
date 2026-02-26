package org.ost.advertisement.ui.views.header.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.fields.PasswordField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.SIGNUP_PASSWORD_LABEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class SignUpPasswordField extends PasswordField {

    public SignUpPasswordField(I18nService i18nService) {
        super(i18nService);
        configure(Parameters.builder()
                .labelKey(SIGNUP_PASSWORD_LABEL)
                .placeholderKey(SIGNUP_PASSWORD_LABEL)
                .required(true)
                .build());
    }
}
