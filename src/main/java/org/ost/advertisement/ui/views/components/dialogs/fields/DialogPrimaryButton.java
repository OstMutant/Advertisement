package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
public class DialogPrimaryButton extends Button {

    @Value
    @Builder
    public static class Parameters {
        @NonNull
        I18nService i18n;
        @NonNull
        I18nKey labelKey;
    }

    public DialogPrimaryButton(Parameters p) {
        super(p.getI18n().get(p.getLabelKey()));
        addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    }
}