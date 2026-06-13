package org.ost.ui.query.elements.action;


@FunctionalInterface
public interface QueryActionBlockHandler {

    void updateDirtyState(boolean dirty);
}
