package org.ost.marketplace.ui.query.elements.fields;

import com.vaadin.flow.component.combobox.ComboBox;
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
public class QueryComboField<T> extends ComboBox<T>
        implements Configurable<QueryComboField<T>, QueryComboField.Parameters<T>>, Translatable, Initialization<QueryComboField<T>> {

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
    public QueryComboField<T> init() {
        addClassName("query-combo");
        setClearButtonVisible(true);
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryComboField<T> configure(Parameters<T> p) {
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setItems(p.getItems());
        return this;
    }
}
