package org.ost.advertisement.ui.views.components.fields;

import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S110")
public class UiPasswordField extends PasswordField implements Configurable<UiPasswordField, UiPasswordField.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        @NonNull I18nKey placeholderKey;
        boolean          required;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UiPasswordField, Parameters> {
        @Getter
        private final ObjectProvider<UiPasswordField> provider;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    public UiPasswordField configure(Parameters p) {
        setLabel(getValue(p.getLabelKey()));
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setRequired(p.isRequired());
        setWidthFull();
        addClassName("password-field");
        return this;
    }

}