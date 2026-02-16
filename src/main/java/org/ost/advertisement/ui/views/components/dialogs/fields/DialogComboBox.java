package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogComboBox<T> extends ComboBox<T> {

    @Value
    @Builder
    public static class Parameters<T> {
        @NonNull
        I18nService i18n;
        @NonNull
        I18nKey labelKey;
        @Singular
        List<T> items;
        boolean required;
    }

    public DialogComboBox(Parameters<T> p) {
        setLabel(p.getI18n().get(p.getLabelKey()));
        setItems(p.getItems());
        setRequired(p.isRequired());
        setAllowCustomValue(false);
        addClassName("dialog-combo-box");
    }
}