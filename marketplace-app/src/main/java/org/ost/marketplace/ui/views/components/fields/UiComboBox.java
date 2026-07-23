package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.combobox.ComboBox;

import java.util.List;

public class UiComboBox<T> extends ComboBox<T> {

    public UiComboBox(String label, List<T> items, boolean required, String testId) {
        setAllowCustomValue(false);
        setWidthFull();
        addClassName("combo-box");
        setLabel(label);
        setItems(items);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
