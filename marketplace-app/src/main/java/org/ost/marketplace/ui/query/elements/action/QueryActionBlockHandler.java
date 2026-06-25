package org.ost.marketplace.ui.query.elements.action;


@FunctionalInterface
public interface QueryActionBlockHandler {

    void updateDirtyState(boolean dirty);
}
