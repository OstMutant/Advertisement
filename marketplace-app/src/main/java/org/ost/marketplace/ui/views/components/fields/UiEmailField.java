package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.EmailField;

public class UiEmailField extends EmailField {

    public UiEmailField(String label, String placeholder, boolean required, String testId) {
        setWidthFull();
        addClassName("email-field");
        setLabel(label);
        setPlaceholder(placeholder);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
