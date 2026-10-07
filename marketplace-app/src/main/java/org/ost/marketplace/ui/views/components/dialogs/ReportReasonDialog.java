package org.ost.marketplace.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import lombok.NonNull;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextArea;

import java.util.function.Consumer;

public final class ReportReasonDialog extends BaseDialog {

    public ReportReasonDialog(@NonNull String title, @NonNull String reasonFieldLabel,
                               @NonNull String confirmLabel, @NonNull String cancelLabel,
                               @NonNull Consumer<String> onConfirm) {
        DialogLayout layout = new DialogLayout();
        buildLayout(layout);
        setHeaderTitle(title);

        UiTextArea reasonField = new UiTextArea(reasonFieldLabel, null, 1000, true, "report-reason-field-text");
        layout.addFormContent(reasonField);

        UiPrimaryButton confirmButton = new UiPrimaryButton(confirmLabel);
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            if (reasonField.getValue() == null || reasonField.getValue().isBlank()) return;
            try {
                onConfirm.accept(reasonField.getValue());
            } finally {
                close();
            }
        });

        UiTertiaryButton cancelButton = new UiTertiaryButton(cancelLabel);
        cancelButton.addClickListener(_ -> close());

        getFooter().add(confirmButton, cancelButton);
    }
}
