package org.ost.advertisement.ui.views.components.query.elements.bar;

import com.vaadin.flow.component.html.Span;

public class SeparatorSpan extends Span {
    public SeparatorSpan() {
        setText("|");
        getStyle().set("margin", "0 8px").set("color", "#999");
    }
}