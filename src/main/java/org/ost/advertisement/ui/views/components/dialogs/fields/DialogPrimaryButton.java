package org.ost.advertisement.ui.views.components.dialogs.fields;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.i18n.I18nLabelParams;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
@SuppressWarnings("java:S110")
public class DialogPrimaryButton extends Button {

    @Value @Builder
    public static class Parameters  implements I18nLabelParams {
        @NonNull I18nService i18nService;
        @NonNull I18nKey labelKey;
    }

    public DialogPrimaryButton(Parameters p) {
        super(p.label());
        addThemeVariants(ButtonVariant.LUMO_PRIMARY);
    }
}