package org.ost.marketplace.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class DialogLayout extends VerticalLayout {

    private final FormLayout form = new FormLayout();
    private final Div bottom = new Div();
    private final Div scrollContainer;

    public DialogLayout() {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.addClassName("dialog-form");
        bottom.addClassName("dialog-bottom");

        scrollContainer = new Div(form);
        scrollContainer.addClassName("scroll-container");

        addClassName("dialog-root");
        setAlignItems(Alignment.STRETCH);
        add(scrollContainer, bottom);
    }

    public void addFormContent(Component... components) {
        form.add(components);
    }

    public void addScrollContent(Component... components) {
        scrollContainer.add(components);
    }

    public void addBottomContent(Component... components) {
        bottom.add(components);
    }
}
