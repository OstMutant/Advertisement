package org.ost.marketplace.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.core.Configurable;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public final class ConfirmActionDialog extends BaseDialog
        implements Configurable<ConfirmActionDialog, ConfirmActionDialog.Parameters>, I18nParams {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey  titleKey;
        @NonNull String   message;
        @NonNull I18nKey  confirmKey;
        @NonNull I18nKey  cancelKey;
        @NonNull Runnable onConfirm;
    }

    // -------------------------------------------------------------------------

    @Getter
    private final transient I18nService                          i18nService;
    private final           DialogLayout                         layout;

    @Override
    @PostConstruct
    protected void buildLayout() {
        super.buildLayout(layout);
    }

    @Override
    public ConfirmActionDialog configure(Parameters p) {
        setHeaderTitle(getValue(p.getTitleKey()));

        Icon warningIcon = VaadinIcon.WARNING.create();
        warningIcon.addClassName("dialog-confirm-icon");

        Paragraph body = new Paragraph(p.getMessage());
        body.addClassName("dialog-confirm-text");

        Div bodyWrapper = new Div(warningIcon, body);
        bodyWrapper.addClassName("dialog-confirm-body");
        layout.addFormContent(bodyWrapper);

        UiPrimaryButton confirmButton = new UiPrimaryButton(getValue(p.getConfirmKey()));
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            try {
                p.getOnConfirm().run();
            } finally {
                close();
            }
        });

        UiTertiaryButton cancelButton = new UiTertiaryButton(getValue(p.getCancelKey()));
        cancelButton.addClickListener(_ -> close());

        getFooter().add(confirmButton, cancelButton);
        return this;
    }
}
