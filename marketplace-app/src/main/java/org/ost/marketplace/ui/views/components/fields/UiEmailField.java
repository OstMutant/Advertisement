package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.EmailField;
import lombok.NonNull;

public class UiEmailField extends EmailField {

    public UiEmailField(@NonNull String label, String placeholder, boolean required, @NonNull String testId) {
        setWidthFull();
        addClassName("email-field");
        setLabel(label);
        setPlaceholder(placeholder);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
