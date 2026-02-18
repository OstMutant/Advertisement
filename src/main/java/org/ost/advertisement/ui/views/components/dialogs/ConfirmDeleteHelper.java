package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfirmDeleteHelper {

    public static void showConfirm(I18nService i18n, String message,
                                   I18nKey confirmKey, I18nKey cancelKey,
                                   Runnable onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(i18n.get(I18nKey.USER_VIEW_CONFIRM_DELETE_TITLE));

        Paragraph body = new Paragraph(message);
        body.addClassName("dialog-confirm-text");

        DialogPrimaryButton confirmButton = new DialogPrimaryButton(DialogPrimaryButton.Parameters.builder()
                .i18n(i18n).labelKey(confirmKey).build());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            try {
                onConfirm.run();
            } finally {
                dialog.close();
            }
        });

        DialogTertiaryButton cancelButton = new DialogTertiaryButton(DialogTertiaryButton.Parameters.builder()
                .i18n(i18n).labelKey(cancelKey).build());
        cancelButton.addClickListener(_ -> dialog.close());

        DialogLayout layout = new DialogLayout();
        layout.addFormContent(body);
        layout.addActions(confirmButton, cancelButton);

        dialog.add(layout.getLayout());
        dialog.open();
    }
}
