package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.combobox.ComboBox;
import lombok.NonNull;

import java.util.List;

public class UiComboBox<T> extends ComboBox<T> {

    public UiComboBox(@NonNull String label, @NonNull List<T> items, boolean required, @NonNull String testId) {
        setAllowCustomValue(false);
        setWidthFull();
        addClassName("combo-box");
        setLabel(label);
        setItems(items);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
