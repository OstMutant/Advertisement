package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.TextArea;

public class UiTextArea extends TextArea {

    public UiTextArea(String label, String placeholder, int maxLength, boolean required, String testId) {
        setWidthFull();
        addClassName("text-area");
        setLabel(label);
        setPlaceholder(placeholder);
        if (maxLength > 0) setMaxLength(maxLength);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
