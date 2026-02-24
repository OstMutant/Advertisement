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
    private final Div viewBody = createViewBody();
    @Getter
    private final Div editBody = createEditBody();
    @Getter
    private final Div metaContainer = createMetaContainer();
    @Getter
    private final Span breadcrumbCurrent = createBreadcrumbCurrent();

    private final Div breadcrumbSlot = createBreadcrumbSlot();
    private final Div headerActions = createHeaderActions();

    @PostConstruct
    private void init() {
        addClassName("overlay__inner");

        Div breadcrumb = createBreadcrumb();
        Div header = createHeader(breadcrumb);
        Div content = createContent();

        add(header, content);
    }

    // --- factory methods ----------------------------------------------------

    private Div createBreadcrumbSlot() {
        Div slot = new Div();
        slot.addClassName("overlay__breadcrumb-back-slot");
        return slot;
    }

    private Span createBreadcrumbCurrent() {
        Span current = new Span();
        current.addClassName("overlay__breadcrumb-current");
        return current;
    }

    private Div createHeaderActions() {
        Div actions = new Div();
        actions.addClassName("overlay__header-actions");
        return actions;
    }

    private Div createViewBody() {
        Div body = new Div();
        body.addClassName("overlay__view-body");
        return body;
    }

    private Div createEditBody() {
        Div body = new Div();
        body.addClassName("overlay__edit-body");
        return body;
    }

    private Div createMetaContainer() {
        Div meta = new Div();
        meta.addClassName("overlay__meta-container");
        return meta;
    }

    private Div createBreadcrumb() {
        Span breadcrumbSep = new Span("›");
        breadcrumbSep.addClassName("overlay__breadcrumb-sep");

        Div breadcrumb = new Div(breadcrumbSlot, breadcrumbSep, breadcrumbCurrent);
        breadcrumb.addClassName("overlay__breadcrumb");
        return breadcrumb;
    }

    private Div createHeader(Div breadcrumb) {
        Div header = new Div(breadcrumb, headerActions);
        header.addClassName("overlay__header");
        return header;
    }

    private Div createContent() {
        Div content = new Div(viewBody, editBody, metaContainer);
        content.addClassName("overlay__content");
        return content;
    }

    // --- public API ---------------------------------------------------------

    public void setBreadcrumbButton(Component button) {
        breadcrumbSlot.add(button);
    }

    public void addHeaderActions(Component... components) {
        headerActions.add(components);
    }

    public void addViewContent(Component... components) {
        viewBody.add(components);
    }

    public void addEditContent(Component... components) {
        editBody.add(components);
    }
}
