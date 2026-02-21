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

    private final FormLayout form = new FormLayout();
    private final HorizontalLayout actions = new HorizontalLayout();
    private final Div bottom = new Div();
    private Div scrollContainer;

    @PostConstruct
    private void init() {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.addClassName("dialog-form");
        actions.addClassName("dialog-actions");
        bottom.addClassName("dialog-bottom");

        scrollContainer = new Div(form);
        scrollContainer.addClassName("scroll-container");

        addClassName("dialog-root");
        setAlignItems(Alignment.STRETCH);
        add(scrollContainer, bottom, actions);
    }

    /** Adds components into the FormLayout — standard path for input fields. */
    public void addFormContent(Component... components) {
        form.add(components);
    }

    /**
     * Adds components directly into the scroll container, bypassing FormLayout.
     * Use when the content manages its own flex/scroll layout.
     */
    public void addScrollContent(Component... components) {
        scrollContainer.add(components);
    }

    /**
     * Adds components to the bottom slot — pinned between the scroll area and
     * the action buttons. Does not scroll with content.
     */
    public void addBottomContent(Component... components) {
        bottom.add(components);
    }

    public void addActions(Component... buttons) {
        actions.add(buttons);
    }
}