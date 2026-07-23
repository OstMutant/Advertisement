package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import lombok.Getter;

public class OverlayLayout extends Div {

    @Getter
    private final Span breadcrumbCurrent = new Span();

    private final Div breadcrumbSlot = new Div();
    private final Div headerActions  = new Div();
    private final Div body           = new Div();

    public OverlayLayout() {
        addClassName("overlay__inner");

        breadcrumbSlot.addClassName("overlay__breadcrumb-back-slot");
        headerActions.addClassName("overlay__header-actions");
        breadcrumbCurrent.addClassName("overlay__breadcrumb-current");
        body.addClassName("overlay__content");

        Span breadcrumbSep = new Span("›");
        breadcrumbSep.addClassName("overlay__breadcrumb-sep");

        Div breadcrumb = new Div(breadcrumbSlot, breadcrumbSep, breadcrumbCurrent);
        breadcrumb.addClassName("overlay__breadcrumb");

        Div header = new Div(breadcrumb, headerActions);
        header.addClassName("overlay__header");

        add(header, body);
    }

    public void setBreadcrumbButton(Component button) {
        breadcrumbSlot.removeAll();
        breadcrumbSlot.add(button);
    }

    public void setHeaderActions(Div actions) {
        headerActions.removeAll();
        headerActions.add(actions);
    }

    public void setContent(Div content) {
        body.removeAll();
        body.add(content);
    }
}
