package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.PasswordField;
import lombok.NonNull;

public class UiPasswordField extends PasswordField {

    public UiPasswordField(@NonNull String label, String placeholder, boolean required, @NonNull String testId) {
        setWidthFull();
        addClassName("password-field");
        setLabel(label);
        setPlaceholder(placeholder);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
