package org.ost.marketplace.ui.query.elements.fields;
import org.ost.marketplace.common.I18nKey;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
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
public class QueryTextField extends TextField
        implements Configurable<QueryTextField, QueryTextField.Parameters>, Initialization<QueryTextField> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey placeholderKey;
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
        setPlaceholder(i18nService.get(p.getPlaceholderKey()));
        return this;
    }
}
