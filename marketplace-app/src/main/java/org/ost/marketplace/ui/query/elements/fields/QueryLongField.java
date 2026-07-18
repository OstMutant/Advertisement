package org.ost.marketplace.ui.query.elements.fields;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;

import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;

import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.views.utils.SupportUtil;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.ui.query.utils.HighlighterUtil.setDefaultBorder;

/**
 * Text-backed whole-number filter field (e.g. an id range). Deliberately not a Vaadin
 * {@code NumberField}/{@code IntegerField}: {@code NumberField} is {@code Double}-backed, which
 * silently truncates fractional input on conversion to the {@code Long} the filter DTO actually
 * needs (improvement-061); {@code IntegerField} is 32-bit, narrower than the {@code BIGSERIAL}
 * ({@code Long}) id columns this field filters. A plain text field parsed directly to {@code Long}
 * via {@link SupportUtil#toLongOrNull} has neither problem. Un-parseable input (e.g. "123.99") is
 * flagged via the component's own native {@code setInvalid}/error-message state -- independent of,
 * and does not conflict with, {@code HighlighterUtil}'s separate dirty/changed CSS-class styling.
 */
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class QueryLongField extends TextField
        implements Configurable<QueryLongField, QueryLongField.Parameters>, Initialization<QueryLongField> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey placeholderKey;
        @NonNull I18nKey invalidNumberMessageKey;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public QueryLongField init() {
        addClassName("query-number");
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        setDefaultBorder(this);
        return this;
    }

    @Override
    public QueryLongField configure(Parameters p) {
        setPlaceholder(i18nService.get(p.getPlaceholderKey()));
        String invalidMessage = i18nService.get(p.getInvalidNumberMessageKey());
        addValueChangeListener(e -> {
            boolean invalid = SupportUtil.nullIfBlank(e.getValue()) != null
                    && SupportUtil.toLongOrNull(e.getValue()) == null;
            setInvalid(invalid);
            setErrorMessage(invalid ? invalidMessage : null);
        });
        return this;
    }
}
