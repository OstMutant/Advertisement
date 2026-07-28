package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import lombok.Getter;

import java.util.List;

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
        setBreadcrumbLinks(List.of(button));
    }

    // Ordered chain of clickable links before the current-page label, e.g. Home / Settings / Activity.
    public void setBreadcrumbLinks(List<Component> links) {
        breadcrumbSlot.removeAll();
        for (int i = 0; i < links.size(); i++) {
            breadcrumbSlot.add(links.get(i));
            if (i < links.size() - 1) {
                Span sep = new Span("›");
                sep.addClassName("overlay__breadcrumb-sep");
                breadcrumbSlot.add(sep);
            }
        }
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
