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

    // exposed for visibility management (Variant B — caller sets visible directly)
    @Getter private Div  viewBody;
    @Getter private Div  editBody;
    @Getter private Div  metaContainer;
    @Getter private Span breadcrumbCurrent;

    private Div breadcrumbSlot;
    private Div headerActions;

    @PostConstruct
    private void init() {
        // OverlayLayout itself carries overlay__inner to preserve the flex chain:
        // .advertisement-overlay (flex column) → .overlay__inner (flex:1, min-height:0) → ...
        addClassName("overlay__inner");

        // -- breadcrumb -------------------------------------------------------
        breadcrumbSlot = new Div();
        breadcrumbSlot.addClassName("overlay__breadcrumb-back-slot");

        Span breadcrumbSep = new Span("›");
        breadcrumbSep.addClassName("overlay__breadcrumb-sep");

        breadcrumbCurrent = new Span();
        breadcrumbCurrent.addClassName("overlay__breadcrumb-current");

        Div breadcrumb = new Div(breadcrumbSlot, breadcrumbSep, breadcrumbCurrent);
        breadcrumb.addClassName("overlay__breadcrumb");

        // -- header actions ---------------------------------------------------
        headerActions = new Div();
        headerActions.addClassName("overlay__header-actions");

        Div header = new Div(breadcrumb, headerActions);
        header.addClassName("overlay__header");

        // -- view body --------------------------------------------------------
        viewBody = new Div();
        viewBody.addClassName("overlay__view-body");

        // -- edit/create body -------------------------------------------------
        editBody = new Div();
        editBody.addClassName("overlay__edit-body");

        // -- meta container (VIEW + EDIT, hidden in CREATE) -------------------
        metaContainer = new Div();
        metaContainer.addClassName("overlay__meta-container");

        Div content = new Div(viewBody, editBody, metaContainer);
        content.addClassName("overlay__content");

        add(header, content);
    }

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