package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.ui.views.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryMultiSelectComboField<T> extends MultiSelectComboBox<T>
        implements Configurable<QueryMultiSelectComboField<T>, QueryMultiSelectComboField.Parameters<T>>, I18nParams, Initialization<QueryMultiSelectComboField<T>> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull I18nKey placeholderKey;
        @NonNull T[]     items;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder<T> extends ComponentBuilder<QueryMultiSelectComboField<T>, Parameters<T>> {
        @Getter
        private final ObjectProvider<QueryMultiSelectComboField<T>> provider;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public QueryMultiSelectComboField<T> init() {
        addClassName("query-multi-combo");
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryMultiSelectComboField<T> configure(Parameters<T> p) {
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setItems(p.getItems());
        return this;
    }
}
