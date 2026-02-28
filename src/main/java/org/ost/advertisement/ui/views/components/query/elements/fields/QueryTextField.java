package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.ui.views.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryTextField extends TextField implements Configurable<QueryTextField, QueryTextField.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey placeholderKey;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<QueryTextField, Parameters> {
        @Getter
        private final ObjectProvider<QueryTextField> provider;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    public QueryTextField configure(Parameters p) {
        addClassName("query-text");
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        return this;
    }

}