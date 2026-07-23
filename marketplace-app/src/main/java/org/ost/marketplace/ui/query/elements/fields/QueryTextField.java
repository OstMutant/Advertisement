package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

public class QueryTextField extends TextField {

    public QueryTextField(String placeholder) {
        addClassName("query-text");
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        setPlaceholder(placeholder);
    }
}
