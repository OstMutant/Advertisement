package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class DialogLayout extends VerticalLayout {

    private final FormLayout form =  new FormLayout();
    private final HorizontalLayout actions = new HorizontalLayout();

    @PostConstruct
    private void init() {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.addClassName("dialog-form");
        actions.addClassName("dialog-actions");

        addClassName("dialog-root");
        add(wrapScrollable(form), actions);
    }

    public void addFormContent(Component... components) {
        form.add(components);
    }

    public void addActions(Component... buttons) {
        actions.add(buttons);
    }

    private Component wrapScrollable(Component inner) {
        Div scroll = new Div(inner);
        scroll.addClassName("scroll-container");
        return scroll;
    }

}