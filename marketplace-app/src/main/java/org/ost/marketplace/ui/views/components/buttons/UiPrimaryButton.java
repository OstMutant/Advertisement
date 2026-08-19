package org.ost.marketplace.ui.views.components.buttons;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;
import lombok.NonNull;

public class UiPrimaryButton extends Button {

    public UiPrimaryButton(@NonNull String label) {
        this(label, null);
    }

    public UiPrimaryButton(@NonNull String label, Icon icon) {
        addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addClassName("primary-button");
        setText(label);
        if (icon != null) setIcon(icon);
    }
}
