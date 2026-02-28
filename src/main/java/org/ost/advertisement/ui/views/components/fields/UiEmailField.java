package org.ost.advertisement.ui.views.components.fields;

import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.utils.builder.Configurable;
import org.ost.advertisement.ui.views.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.views.utils.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S110")
public class UiEmailField extends EmailField implements Configurable<UiEmailField, UiEmailField.Parameters>, I18nParams {

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
    public UiEmailField configure(Parameters p) {
        setLabel(getValue(p.getLabelKey()));
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setRequired(p.isRequired());
        setWidthFull();
        addClassName("email-field");
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UiEmailField, Parameters> {
        @Getter
        private final ObjectProvider<UiEmailField> provider;
    }
}
