package org.ost.marketplace.ui.views.components.buttons.action;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

public class ShareActionButton extends BaseActionButton {

    public ShareActionButton(String tooltip, Runnable onClick) {
        this(tooltip, onClick, null, false);
    }

    public ShareActionButton(String tooltip, Runnable onClick, String cssClassName, boolean small) {
        setIcon(VaadinIcon.SHARE.create());
        addThemeVariants(small
                ? new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL}
                : new ButtonVariant[]{ButtonVariant.LUMO_TERTIARY_INLINE});
        applyConfig(tooltip, onClick, cssClassName);
    }
}
