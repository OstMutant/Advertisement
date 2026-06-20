package org.ost.marketplace.ui.query.elements.fields;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;

import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;

import org.ost.marketplace.ui.core.Initialization;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryMultiSelectComboField<T> extends MultiSelectComboBox<T>
        implements Configurable<QueryMultiSelectComboField<T>, QueryMultiSelectComboField.Parameters<T>>, Initialization<QueryMultiSelectComboField<T>> {

    @Value
    @lombok.Builder
    public static class Parameters<T> {
        @NonNull I18nKey placeholderKey;
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
        setPlaceholder(i18nService.get(p.getPlaceholderKey()));
        setItems(p.getItems());
        return this;
    }
}
