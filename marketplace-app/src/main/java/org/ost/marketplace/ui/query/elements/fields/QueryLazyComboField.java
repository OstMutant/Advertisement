package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.core.Initialization;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class QueryLazyComboField<T> extends ComboBox<T>
        implements Configurable<QueryLazyComboField<T>, QueryLazyComboField.Parameters<T>>,
                   Initialization<QueryLazyComboField<T>> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull I18nKey                                       placeholderKey;
        @NonNull ItemLabelGenerator<T>                         labelGenerator;
        @NonNull CallbackDataProvider.FetchCallback<T, String> fetchCallback;
        @NonNull CallbackDataProvider.CountCallback<T, String> countCallback;
    }

    @Getter
    private final transient I18nService i18nService;

    public QueryLazyComboField(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @Override
    @PostConstruct
    public QueryLazyComboField<T> init() {
        addClassName("query-lazy-combo");
        setClearButtonVisible(true);
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryLazyComboField<T> configure(Parameters<T> p) {
        setPlaceholder(i18nService.get(p.getPlaceholderKey()));
        setItemLabelGenerator(p.getLabelGenerator());
        setItems(DataProvider.fromFilteringCallbacks(p.getFetchCallback(), p.getCountCallback()));
        return this;
    }
}
