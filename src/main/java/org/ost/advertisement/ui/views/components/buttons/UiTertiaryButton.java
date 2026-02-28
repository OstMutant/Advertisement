package org.ost.advertisement.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiTertiaryButton extends Button implements Configurable<UiTertiaryButton, UiTertiaryButton.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UiTertiaryButton, Parameters> {
        @Getter
        private final ObjectProvider<UiTertiaryButton> provider;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    public UiTertiaryButton configure(Parameters p) {
        setText(getValue(p.getLabelKey()));
        addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        addClassName("tertiary-button");
        return this;
    }

}