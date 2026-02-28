package org.ost.advertisement.ui.views.components.query.elements.fields;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.utils.builder.Configurable;
import org.ost.advertisement.ui.views.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.views.utils.I18nParams;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.ui.views.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryMultiSelectComboField<T> extends MultiSelectComboBox<T> implements Configurable<QueryMultiSelectComboField<T>, QueryMultiSelectComboField.Parameters<T>>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull I18nKey placeholderKey;
        @NonNull T[]     items;
    }

    @Override
    public QueryMultiSelectComboField<T> configure(Parameters<T> p) {
        addClassName("query-multi-combo");
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setItems(p.getItems());
        setDefaultBorder(this);
        return this;
    }
    
    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder<T> extends ComponentBuilder<QueryMultiSelectComboField<T>, Parameters<T>> {
        @Getter
        private final ObjectProvider<QueryMultiSelectComboField<T>> provider;
    }
}