package org.ost.marketplace.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import lombok.NonNull;

public class UiTertiaryButton extends Button {

    public UiTertiaryButton(@NonNull String label) {
        addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        addClassName("tertiary-button");
        setText(label);
    }
}
