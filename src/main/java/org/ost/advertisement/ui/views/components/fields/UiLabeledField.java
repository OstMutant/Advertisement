package org.ost.advertisement.ui.views.components.fields;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.i18n.I18nParams;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@SuppressWarnings("java:S110")
public class UiLabeledField extends HorizontalLayout implements Configurable<UiLabeledField, UiLabeledField.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey labelKey;
        String           value;
    }

    private Span valueSpan;

    @Override
    public UiLabeledField configure(Parameters p) {
        Span labelSpan = new Span(getValue(p.getLabelKey()) + ":");
        labelSpan.addClassName("labeled-field__label");

        valueSpan = new Span(p.getValue() != null ? p.getValue() : "");
        valueSpan.addClassName("labeled-field__value");

        add(labelSpan, valueSpan);
        setAlignItems(Alignment.BASELINE);
        addClassName("labeled-field");
        return this;
    }

    public void update(String value) {
        if (valueSpan != null) valueSpan.setText(value != null ? value : "");
    }
}
