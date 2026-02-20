package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DialogStyle {

    public static void apply(Dialog dialog, String titleText) {
        dialog.setDraggable(false);
        dialog.setResizable(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.setCloseOnEsc(true);
        dialog.setHeaderTitle(titleText);
    }

}