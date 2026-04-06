package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class DialogLayout extends VerticalLayout implements Initialization<DialogLayout> {

    private final FormLayout form = new FormLayout();
    private final Div bottom = new Div();
    private Div scrollContainer;

    @Override
    @PostConstruct
    public DialogLayout init() {
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.addClassName("dialog-form");
        bottom.addClassName("dialog-bottom");

        scrollContainer = new Div(form);
        scrollContainer.addClassName("scroll-container");

        addClassName("dialog-root");
        setAlignItems(Alignment.STRETCH);
        add(scrollContainer, bottom);
        return this;
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
