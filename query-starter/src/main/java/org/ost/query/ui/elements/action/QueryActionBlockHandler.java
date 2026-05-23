package org.ost.marketplace.ui.views.components.query.elements.action;


@FunctionalInterface
public interface QueryActionBlockHandler {

    void updateDirtyState(boolean dirty);
}
