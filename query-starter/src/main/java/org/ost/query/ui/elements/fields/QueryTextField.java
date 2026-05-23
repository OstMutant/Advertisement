package org.ost.query.ui.elements.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.platform.core.i18n.TranslationKey;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.ComponentBuilder;
import org.ost.platform.core.i18n.Translatable;
import org.ost.platform.ui.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.query.ui.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryTextField extends TextField
        implements Configurable<QueryTextField, QueryTextField.Parameters>, Translatable, Initialization<QueryTextField> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull TranslationKey placeholderKey;
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
    @PostConstruct
    public QueryTextField init() {
        addClassName("query-text");
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryTextField configure(Parameters p) {
        setPlaceholder(getValue(p.getPlaceholderKey()));
        return this;
    }
}
