package org.ost.marketplace.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.core.Initialization;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiIconButton extends Button
        implements Configurable<UiIconButton, UiIconButton.Parameters>, I18nParams, Initialization<UiIconButton> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        @NonNull Icon    icon;
        /** Renders with {@code LUMO_TERTIARY_INLINE} instead of {@code LUMO_TERTIARY} — matches
         *  Vaadin's own treatment for buttons placed in a text field's prefix/suffix slot (no
         *  button-like padding/hover ring). Defaults to {@code false} (the normal, non-inline case). */
        boolean inline;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public UiIconButton init() {
        addClassName("icon-button");
        return this;
    }

    @Override
    public UiIconButton configure(Parameters p) {
        addThemeVariants(p.isInline() ? ButtonVariant.LUMO_TERTIARY_INLINE : ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_ICON);
        setIcon(p.getIcon());
        String label = getValue(p.getLabelKey());
        getElement().setAttribute("title", label);
        // icon-only button -- title alone isn't a reliable accessible name across screen readers
        getElement().setAttribute("aria-label", label);
        return this;
    }
}
