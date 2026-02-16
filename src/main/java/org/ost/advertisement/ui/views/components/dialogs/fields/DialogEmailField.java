package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.textfield.EmailField;
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
public class DialogEmailField extends EmailField {

    @Value
    @Builder
    public static class Parameters {
        @NonNull
        I18nService i18n;
        @NonNull
        I18nKey labelKey;
        @NonNull
        I18nKey placeholderKey;
        boolean required;
    }

    public DialogEmailField(Parameters p) {
        setLabel(p.getI18n().get(p.getLabelKey()));
        setPlaceholder(p.getI18n().get(p.getPlaceholderKey()));
        setRequired(p.isRequired());
        addClassName("dialog-email-field");
    }
}