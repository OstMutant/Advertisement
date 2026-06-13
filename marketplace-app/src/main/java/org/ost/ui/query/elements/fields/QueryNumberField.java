package org.ost.ui.query.elements.fields;

import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.i18n.TranslationKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.marketplace.i18n.Translatable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import static org.ost.ui.query.utils.HighlighterUtil.setDefaultBorder;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryNumberField extends NumberField
        implements Configurable<QueryNumberField, QueryNumberField.Parameters>, Translatable, Initialization<QueryNumberField> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull TranslationKey placeholderKey;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public QueryNumberField init() {
        addClassName("query-number");
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryNumberField configure(Parameters p) {
        setPlaceholder(getValue(p.getPlaceholderKey()));
        return this;
    }
}
