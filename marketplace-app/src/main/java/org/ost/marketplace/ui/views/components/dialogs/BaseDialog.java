package org.ost.marketplace.ui.views.components.dialogs;

import com.vaadin.flow.component.dialog.Dialog;

public abstract class BaseDialog extends Dialog {

    protected void buildLayout() {
    }

    protected void buildLayout(DialogLayout layout) {
        setDraggable(false);
        setResizable(false);
        setCloseOnOutsideClick(false);
        setCloseOnEsc(true);
        add(layout);
    }
}
