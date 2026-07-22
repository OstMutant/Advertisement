package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.TextField;

public class UiTextField extends TextField {

    public UiTextField(String label, String placeholder, int maxLength, boolean required, String testId) {
        setWidthFull();
        addClassName("text-field");
        setLabel(label);
        setPlaceholder(placeholder);
        if (maxLength > 0) setMaxLength(maxLength);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
