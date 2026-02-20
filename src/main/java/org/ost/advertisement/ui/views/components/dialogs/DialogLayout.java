package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class DialogLayout {

    private final VerticalLayout root = new VerticalLayout();
    private final FormLayout form = new FormLayout();
    private final HorizontalLayout actions = new HorizontalLayout();

    public DialogLayout() {
        applyFormLayout(form);

        root.addClassName("dialog-root");
        form.addClassName("dialog-form");
        actions.addClassName("dialog-actions");

        root.add(wrapScrollable(form), actions);
    }

    public void addFormContent(Component... components) {
        form.add(components);
    }

    public void addActions(Component... buttons) {
        actions.add(buttons);
    }

    public Component getLayout() {
        return root;
    }

    public static Component wrapScrollable(Component inner) {
        Div scroll = new Div(inner);
        scroll.addClassName("scroll-container");
        return scroll;
    }

    public static void applyFormLayout(FormLayout form) {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
    }
}