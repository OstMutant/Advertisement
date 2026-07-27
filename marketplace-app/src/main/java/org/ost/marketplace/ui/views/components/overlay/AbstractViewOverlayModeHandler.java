package org.ost.marketplace.ui.views.components.overlay;

import com.vaadin.flow.component.html.Div;

public abstract class AbstractViewOverlayModeHandler implements OverlayModeHandler {

    @Override
    public final void activate(OverlayLayout layout) {
        layout.setContent(buildPrimaryContent());
        layout.setHeaderActions(buildHeaderActions());
    }

    protected abstract Div buildPrimaryContent();

    protected abstract Div buildHeaderActions();
}
