package org.ost.advertisement.ui.views.header.dialogs.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.fields.TextField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.SIGNUP_NAME_LABEL;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class SignUpNameField extends TextField {

    public SignUpNameField(I18nService i18nService) {
        super(i18nService);
        configure(Parameters.builder()
                .labelKey(SIGNUP_NAME_LABEL)
                .placeholderKey(SIGNUP_NAME_LABEL)
                .maxLength(255)
                .required(true)
                .build());
    }
}
