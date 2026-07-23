package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

public class QueryMultiSelectComboField<T> extends MultiSelectComboBox<T> {

    public QueryMultiSelectComboField(String placeholder, T[] items) {
        addClassName("query-multi-combo");
        setDefaultBorder(this);
        setPlaceholder(placeholder);
        setItems(items);
    }
}
