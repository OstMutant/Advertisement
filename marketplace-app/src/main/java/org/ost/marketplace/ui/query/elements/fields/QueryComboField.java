package org.ost.marketplace.ui.query.elements.fields;
import org.ost.marketplace.common.I18nKey;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;

import org.ost.marketplace.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;

import org.ost.marketplace.ui.core.Initialization;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryComboField<T> extends ComboBox<T>
        implements Configurable<QueryComboField<T>, QueryComboField.Parameters<T>>, Initialization<QueryComboField<T>> {

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
    public QueryComboField<T> init() {
        addClassName("query-combo");
        setClearButtonVisible(true);
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryComboField<T> configure(Parameters<T> p) {
        setPlaceholder(i18nService.get(p.getPlaceholderKey()));
        setItems(p.getItems());
        return this;
    }
}
