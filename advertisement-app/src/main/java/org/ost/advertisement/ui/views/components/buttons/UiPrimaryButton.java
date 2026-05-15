package org.ost.advertisement.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.common.I18nKey;
import org.ost.advertisement.i18n.I18nService;
import org.ost.advertisement.ui.rules.Configurable;
import org.ost.advertisement.ui.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.rules.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiPrimaryButton extends Button
        implements Configurable<UiPrimaryButton, UiPrimaryButton.Parameters>, I18nParams, Initialization<UiPrimaryButton> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        Icon icon;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UiPrimaryButton, Parameters> {
        @Getter
        private final ObjectProvider<UiPrimaryButton> provider;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public UiPrimaryButton init() {
        addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addClassName("primary-button");
        return this;
    }

    @Override
    public UiPrimaryButton configure(Parameters p) {
        setText(getValue(p.getLabelKey()));
        if (p.getIcon() != null) setIcon(p.getIcon());
        return this;
    }
}
