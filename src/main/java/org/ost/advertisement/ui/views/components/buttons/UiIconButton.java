package org.ost.advertisement.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
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
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiIconButton extends Button implements Configurable<UiIconButton, UiIconButton.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        @NonNull Icon    icon;
    }

    @Override
    public UiIconButton configure(Parameters p) {
        setIcon(p.getIcon());
        getElement().setAttribute("title", getValue(p.getLabelKey()));
        addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        addClassName("icon-button");
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UiIconButton, Parameters> {
        @Getter
        private final ObjectProvider<UiIconButton> provider;
    }
}
