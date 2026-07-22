package org.ost.marketplace.ui.views.components.overlay.fields;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;

public class OverlayBreadcrumbBackButton extends Button {

    public OverlayBreadcrumbBackButton(String label) {
        addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        addClassName("overlay__breadcrumb-back");
        setText(label);
    }
}
