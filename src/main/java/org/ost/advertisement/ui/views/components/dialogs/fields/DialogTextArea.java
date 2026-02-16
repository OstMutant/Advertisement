package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogTextArea extends TextArea {

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

    public DialogTextArea(Parameters p) {
        setLabel(p.getI18n().get(p.getLabelKey()));
        setPlaceholder(p.getI18n().get(p.getPlaceholderKey()));
        if (p.getMaxLength() > 0) setMaxLength(p.getMaxLength());
        setRequired(p.isRequired());
        addClassName("dialog-text-area");
    }
}