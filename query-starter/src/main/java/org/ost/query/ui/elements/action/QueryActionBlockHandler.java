package org.ost.query.ui.elements.action;


@FunctionalInterface
public interface QueryActionBlockHandler {

    void updateDirtyState(boolean dirty);
}
