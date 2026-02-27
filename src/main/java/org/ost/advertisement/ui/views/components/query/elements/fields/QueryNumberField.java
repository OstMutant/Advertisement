package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.utils.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.ui.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryNumberField extends NumberField implements Configurable<QueryNumberField, QueryNumberField.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey placeholderKey;
    }

    @Override
    public QueryNumberField configure(Parameters p) {
        addClassName("query-number");
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<QueryNumberField, Parameters> {
        @Getter
        private final ObjectProvider<QueryNumberField> provider;
    }
}

