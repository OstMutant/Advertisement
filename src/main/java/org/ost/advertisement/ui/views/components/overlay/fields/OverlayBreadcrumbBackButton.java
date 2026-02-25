package org.ost.advertisement.ui.views.components.overlay.fields;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.i18n.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class OverlayBreadcrumbBackButton extends Button implements Configurable<OverlayBreadcrumbBackButton, OverlayBreadcrumbBackButton.Parameters>, I18nParams {
    @Getter
    private final transient I18nService i18n;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull
        I18nKey labelKey;
    }


    @Override
    public OverlayBreadcrumbBackButton configure(Parameters params) {
        setText(getValue(params.getLabelKey()));
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        addClassName("overlay__breadcrumb-back");
        return this;
    }
}