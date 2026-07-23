package org.ost.marketplace.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

public class UiTertiaryButton extends Button {

    public UiTertiaryButton(String label) {
        addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        addClassName("tertiary-button");
        setText(label);
    }
}
