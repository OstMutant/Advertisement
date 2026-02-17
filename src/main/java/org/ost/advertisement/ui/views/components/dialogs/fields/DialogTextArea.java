package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.i18n.I18nLabelParams;
import org.ost.advertisement.ui.utils.i18n.I18nPlaceholderParams;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogTextArea extends TextArea {

    @Value @Builder
    public static class Parameters implements I18nLabelParams, I18nPlaceholderParams {
        @NonNull I18nService i18n;
        @NonNull I18nKey labelKey;
        @NonNull I18nKey placeholderKey;
        int maxLength;
        boolean required;
    }

    public DialogTextArea(Parameters p) {
        setLabel(p.label());
        setPlaceholder(p.placeholder());
        if (p.getMaxLength() > 0) setMaxLength(p.getMaxLength());
        setRequired(p.isRequired());
        addClassName("dialog-text-area");
    }
}