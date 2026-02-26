package org.ost.advertisement.ui.views.components.fields;

import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.i18n.I18nParams;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S110")
public class EmailField extends com.vaadin.flow.component.textfield.EmailField
        implements Configurable<EmailField, EmailField.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        @NonNull I18nKey placeholderKey;
        boolean          required;
    }

    @Override
    public EmailField configure(Parameters p) {
        setLabel(getValue(p.getLabelKey()));
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setRequired(p.isRequired());
        setWidthFull();
        addClassName("email-field");
        return this;
    }
}
