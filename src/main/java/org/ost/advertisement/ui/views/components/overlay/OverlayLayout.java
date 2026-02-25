package org.ost.advertisement.ui.views.components.overlay;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class OverlayLayout extends Div {

    @Getter
    private final Span breadcrumbCurrent = new Span();

    private final Div breadcrumbSlot = new Div();
    private final Div headerActions  = new Div();
    private final Div body           = new Div();

    @PostConstruct
    private void init() {
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