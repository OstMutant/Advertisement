package org.ost.marketplace.ui.views.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class EmptyStateView extends VerticalLayout {

    public EmptyStateView(VaadinIcon icon, String title, String hint) {
        addClassName("empty-state");
        setAlignItems(Alignment.CENTER);

        Icon iconComponent = icon.create();
        iconComponent.addClassName("empty-state-icon");

        Span titleSpan = new Span(title);
        titleSpan.addClassName("empty-state-title");

        Span hintSpan = new Span(hint);
        hintSpan.addClassName("empty-state-hint");

        add(iconComponent, titleSpan, hintSpan);
    }
}
