package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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

    public static void applyTitle(H2 title) {
        title.addClassName("dialog-title");
    }

    public static void applyFormLayout(FormLayout form) {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.setWidthFull();
    }

    public static void applyActionsLayout(HorizontalLayout actions) {
        actions.setSpacing(true);
        actions.setJustifyContentMode(JustifyContentMode.END);
        actions.setWidthFull();
    }

    public static void applyRootLayout(VerticalLayout root) {
        root.setPadding(true);
        root.setSpacing(false);
        root.setHeight("100%");
    }

    public static Component wrapScrollable(Component inner) {
        Div scroll = new Div(inner);
        scroll.addClassName("scroll-container");
        return scroll;
    }
}

