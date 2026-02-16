package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@SpringComponent
@UIScope
@SuppressWarnings("java:S110")
public class DialogTextField extends TextField {

    @Value
    @Builder
    public static class Parameters {
        @NonNull
        I18nService i18n;
        @NonNull
        I18nKey labelKey;
        @NonNull
        I18nKey placeholderKey;
        int maxLength;
        boolean required;
    }

    public DialogTextField(Parameters p) {
        setLabel(p.getI18n().get(p.getLabelKey()));
        setPlaceholder(p.getI18n().get(p.getPlaceholderKey()));
        if (p.getMaxLength() > 0) setMaxLength(p.getMaxLength());
        setRequired(p.isRequired());
        addClassName("dialog-text-field");
    }
}