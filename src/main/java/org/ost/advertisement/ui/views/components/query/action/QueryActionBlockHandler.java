package org.ost.advertisement.ui.views.components.query.action;


@FunctionalInterface
public interface QueryActionBlockHandler {

    void updateDirtyState(boolean dirty);
}
