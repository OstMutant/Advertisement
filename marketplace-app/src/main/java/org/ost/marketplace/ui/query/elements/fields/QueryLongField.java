package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.ost.marketplace.ui.views.utils.SupportUtil;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

// Text-backed Long filter, not NumberField (Double-backed, truncates fractions) or IntegerField (32-bit, narrower than BIGSERIAL ids).
public class QueryLongField extends TextField {

    public QueryLongField(String placeholder, String invalidMessage) {
        addClassName("query-number");
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        setPlaceholder(placeholder);
        addValueChangeListener(e -> {
            boolean invalid = SupportUtil.nullIfBlank(e.getValue()) != null
                    && SupportUtil.toLongOrNull(e.getValue()) == null;
            setInvalid(invalid);
            setErrorMessage(invalid ? invalidMessage : null);
        });
    }
}
