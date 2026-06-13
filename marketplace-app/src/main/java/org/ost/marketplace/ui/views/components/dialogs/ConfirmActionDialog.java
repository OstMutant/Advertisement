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
import org.ost.marketplace.common.I18nKey;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.ui.Configurable;
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
    private final transient ComponentFactory<UiPrimaryButton>    primaryButtonFactory;
    private final transient ComponentFactory<UiTertiaryButton>   tertiaryButtonFactory;

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

        UiPrimaryButton confirmButton = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(p.getConfirmKey()).build());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            try {
                p.getOnConfirm().run();
            } finally {
                close();
            }
        });

        UiTertiaryButton cancelButton = tertiaryButtonFactory.build(
                UiTertiaryButton.Parameters.builder().labelKey(p.getCancelKey()).build());
        cancelButton.addClickListener(_ -> close());

        getFooter().add(confirmButton, cancelButton);
        return this;
    }
}
