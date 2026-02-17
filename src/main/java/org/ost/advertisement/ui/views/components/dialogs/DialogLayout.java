package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import static org.ost.advertisement.ui.views.components.dialogs.DialogStyle.wrapScrollable;

public class DialogLayout {

    private final VerticalLayout root = new VerticalLayout();
    private final FormLayout form = new FormLayout();
    private final HorizontalLayout actions = new HorizontalLayout();

    public DialogLayout() {
        DialogStyle.applyFormLayout(form);

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
}