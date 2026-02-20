package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public final class ConfirmDeleteDialog extends BaseDialog {
    @Getter
    private final transient I18nService i18n;
    @Getter
    private final transient DialogLayout layout;

    @Override
    @PostConstruct
    protected void init() {
        super.init();
    }

    public ConfirmDeleteDialog showConfirm(String message,
                                           I18nKey confirmKey, I18nKey cancelKey,
                                           Runnable onConfirm) {
        setHeaderTitle(i18n.get(I18nKey.USER_VIEW_CONFIRM_DELETE_TITLE));

        Paragraph body = new Paragraph(message);
        body.addClassName("dialog-confirm-text");

        DialogPrimaryButton confirmButton = new DialogPrimaryButton(DialogPrimaryButton.Parameters.builder()
                .i18n(i18n).labelKey(confirmKey).build());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            try {
                onConfirm.run();
            } finally {
                close();
            }
        });

        DialogTertiaryButton cancelButton = new DialogTertiaryButton(DialogTertiaryButton.Parameters.builder()
                .i18n(i18n).labelKey(cancelKey).build());
        cancelButton.addClickListener(_ -> close());


        layout.addFormContent(body);
        layout.addActions(confirmButton, cancelButton);

        add(layout);
        return this;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder {
        private final ObjectProvider<ConfirmDeleteDialog> provider;

        public ConfirmDeleteDialog build(String message,
                                         I18nKey confirmKey, I18nKey cancelKey,
                                         Runnable onConfirm) {
            return provider.getObject().showConfirm(message, confirmKey, cancelKey, onConfirm);
        }
    }
}
