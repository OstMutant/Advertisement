package org.ost.marketplace.ui.views.components.fields;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.core.Initialization;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiTextArea extends TextArea
        implements Configurable<UiTextArea, UiTextArea.Parameters>, I18nParams, Initialization<UiTextArea> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        @NonNull I18nKey placeholderKey;
        int              maxLength;
        boolean          required;
    }

    @Getter
    private final transient I18nService i18nService;

    @Override
    @PostConstruct
    public UiTextArea init() {
        setWidthFull();
        addClassName("text-area");
        return this;
    }

    @Override
    public UiTextArea configure(Parameters p) {
        setLabel(getValue(p.getLabelKey()));
        setPlaceholder(getValue(p.getPlaceholderKey()));
        if (p.getMaxLength() > 0) setMaxLength(p.getMaxLength());
        setRequired(p.isRequired());
        getElement().setAttribute("data-testid", p.getLabelKey().toTestId());
        return this;
    }
}
