package org.ost.marketplace.ui.views.components.buttons.action;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

public class DeleteActionButton extends BaseActionButton {

    public DeleteActionButton(String tooltip, Runnable onClick) {
        this(tooltip, onClick, null, false);
    }

    public DeleteActionButton(String tooltip, Runnable onClick, String cssClassName, boolean small) {
        setIcon(VaadinIcon.TRASH.create());
        addThemeVariants(small
                ? new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL}
                : new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ERROR});
        applyConfig(tooltip, onClick, cssClassName);
    }
}
