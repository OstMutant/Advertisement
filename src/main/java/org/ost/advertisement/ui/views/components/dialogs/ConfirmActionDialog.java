package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.rules.Configurable;
import org.ost.advertisement.ui.views.rules.ComponentBuilder;
import org.ost.advertisement.ui.views.rules.I18nParams;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.buttons.UiTertiaryButton;
import org.springframework.beans.factory.ObjectProvider;
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

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<ConfirmActionDialog, Parameters> {
        @Getter
        private final ObjectProvider<ConfirmActionDialog> provider;
    }

    // -------------------------------------------------------------------------

    @Getter
    private final transient I18nService               i18nService;
    private final           DialogLayout              layout;
    private final transient UiPrimaryButton.Builder   confirmButtonBuilder;
    private final transient UiTertiaryButton.Builder  cancelButtonBuilder;

    @Override
    protected void buildLayout() {
        super.buildLayout(layout);
    }

    @Override
    public ConfirmActionDialog configure(Parameters p) {
        setHeaderTitle(getValue(p.getTitleKey()));

        Paragraph body = new Paragraph(p.getMessage());
        body.addClassName("dialog-confirm-text");
        layout.addFormContent(body);

        UiPrimaryButton confirmButton = confirmButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(p.getConfirmKey()).build());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            try {
                p.getOnConfirm().run();
            } finally {
                close();
            }
        });

        UiTertiaryButton cancelButton = cancelButtonBuilder.build(
                UiTertiaryButton.Parameters.builder().labelKey(p.getCancelKey()).build());
        cancelButton.addClickListener(_ -> close());

        getFooter().add(confirmButton, cancelButton);
        return this;
    }
}
