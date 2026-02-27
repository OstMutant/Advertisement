package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.utils.builder.Configurable;
import org.ost.advertisement.ui.utils.builder.ComponentBuilder;
import org.ost.advertisement.ui.views.components.fields.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.fields.UiTertiaryButton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public final class ConfirmActionDialog extends BaseDialog
        implements Configurable<ConfirmActionDialog, ConfirmActionDialog.Parameters> {

    @Getter
    private final transient I18nService         i18n;
    @Getter
    private final transient DialogLayout        layout;
    @Getter
    private final transient NotificationService notificationService;
    private final transient UiPrimaryButton.Builder       confirmButtonBuilder;
    private final transient UiTertiaryButton.Builder      cancelButtonBuilder;

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull I18nKey  titleKey;
        @NonNull String   message;
        @NonNull I18nKey  confirmKey;
        @NonNull I18nKey  cancelKey;
        @NonNull Runnable onConfirm;
    }

    @Override
    @PostConstruct
    protected void init() {
        super.init();
    }

    @Override
    public ConfirmActionDialog configure(Parameters p) {
        setHeaderTitle(i18n.get(p.getTitleKey()));

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

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<ConfirmActionDialog, Parameters> {
        @Getter
        private final ObjectProvider<ConfirmActionDialog> provider;
    }
}
