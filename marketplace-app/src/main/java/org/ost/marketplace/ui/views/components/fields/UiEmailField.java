package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.core.Initialization;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiEmailField extends EmailField
        implements Configurable<UiEmailField, UiEmailField.Parameters>, I18nParams, Initialization<UiEmailField> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        @NonNull I18nKey placeholderKey;
        boolean          required;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public UiEmailField init() {
        setWidthFull();
        addClassName("email-field");
        return this;
    }

    @Override
    public UiEmailField configure(Parameters p) {
        setLabel(getValue(p.getLabelKey()));
        setPlaceholder(getValue(p.getPlaceholderKey()));
        setRequired(p.isRequired());
        getElement().setAttribute("data-testid", p.getLabelKey().toTestId());
        return this;
    }
}
