package org.ost.advertisement.ui.views.components.fields;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.rules.Initialization;
import org.springframework.beans.factory.ObjectProvider;
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

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<UiTextArea, Parameters> {
        @Getter
        private final ObjectProvider<UiTextArea> provider;
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
        return this;
    }
}
