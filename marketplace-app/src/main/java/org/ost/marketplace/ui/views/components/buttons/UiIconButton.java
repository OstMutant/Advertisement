package org.ost.marketplace.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;

public class UiIconButton extends Button {

    public UiIconButton(String label, Icon icon) {
        this(label, icon, false);
    }

    // inline: LUMO_TERTIARY_INLINE for buttons in a text field's prefix/suffix slot, no hover ring.
    public UiIconButton(String label, Icon icon, boolean inline) {
        addClassName("icon-button");
        addThemeVariants(inline ? ButtonVariant.LUMO_TERTIARY_INLINE : ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_ICON);
        setIcon(icon);
        getElement().setAttribute("title", label);
        // icon-only button -- title alone isn't a reliable accessible name across screen readers
        getElement().setAttribute("aria-label", label);
    }
}
