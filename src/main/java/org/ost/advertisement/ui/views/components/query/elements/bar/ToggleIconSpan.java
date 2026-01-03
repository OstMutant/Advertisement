package org.ost.advertisement.ui.views.components.query.elements.bar;

import com.vaadin.flow.component.html.Span;

public class ToggleIconSpan extends Span {
    public ToggleIconSpan() {
        getStyle().set("margin-right", "8px").set("font-weight", "bold");
        setText("▸");
    }

    public void setOpen(boolean open) {
        setText(open ? "▾" : "▸");
    }
}