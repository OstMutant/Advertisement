package org.ost.advertisement.ui.views.components.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.*;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.i18n.I18nParams;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@SuppressWarnings("java:S110")
public class UiTextField extends TextField implements Configurable<UiTextField, UiTextField.Parameters>, I18nParams {

    @Getter
    private final transient I18nService i18nService;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull
        I18nKey labelKey;
        @NonNull
        I18nKey placeholderKey;
        int maxLength;
        boolean required;
    }

    @Override
    public UiTextField configure(Parameters p) {
        setLabel(getValue(p.getLabelKey()));
        setPlaceholder(getValue(p.getPlaceholderKey()));
        if (p.getMaxLength() > 0) setMaxLength(p.getMaxLength());
        setRequired(p.isRequired());
        setWidthFull();
        addClassName("text-field");
        return this;
    }
}
