package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.PasswordField;

public class UiPasswordField extends PasswordField {

    public UiPasswordField(String label, String placeholder, boolean required, String testId) {
        setWidthFull();
        addClassName("password-field");
        setLabel(label);
        setPlaceholder(placeholder);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
