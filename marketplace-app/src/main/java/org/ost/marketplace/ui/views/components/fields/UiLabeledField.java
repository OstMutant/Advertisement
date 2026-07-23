package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class UiLabeledField extends HorizontalLayout {

    private final Span valueSpan;

    public UiLabeledField(String label, String value) {
        setAlignItems(Alignment.BASELINE);
        addClassName("labeled-field");

        Span labelSpan = new Span(label + ":");
        labelSpan.addClassName("labeled-field__label");

        valueSpan = new Span(value != null ? value : "");
        valueSpan.addClassName("labeled-field__value");

        add(labelSpan, valueSpan);
    }

    public void update(String value) {
        valueSpan.setText(value != null ? value : "");
    }
}
