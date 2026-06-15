package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.i18n.TranslationKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.i18n.Translatable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryMultiSelectComboField<T> extends MultiSelectComboBox<T>
        implements Configurable<QueryMultiSelectComboField<T>, QueryMultiSelectComboField.Parameters<T>>, Translatable, Initialization<QueryMultiSelectComboField<T>> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull TranslationKey placeholderKey;
        @NonNull T[]     items;
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
