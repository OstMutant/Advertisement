package org.ost.advertisement.ui.views.components.query.elements.bar;

import com.vaadin.flow.component.html.Span;

public class SeparatorSpan extends Span {
    public SeparatorSpan() {
        setText("|");
        addClassName("query-status-bar-separator");
    }
}
