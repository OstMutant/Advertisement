package org.ost.marketplace.ui.views.components.dialogs;

import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;

public final class ConfirmActionDialog extends BaseDialog {

    private final DialogLayout layout = new DialogLayout();

    public ConfirmActionDialog(String title, String message, String confirmLabel, String cancelLabel, Runnable onConfirm) {
        buildLayout(layout);
        setHeaderTitle(title);

        Icon warningIcon = VaadinIcon.WARNING.create();
        warningIcon.addClassName("dialog-confirm-icon");

        Paragraph body = new Paragraph(message);
        body.addClassName("dialog-confirm-text");

        Div bodyWrapper = new Div(warningIcon, body);
        bodyWrapper.addClassName("dialog-confirm-body");
        layout.addFormContent(bodyWrapper);

        UiPrimaryButton confirmButton = new UiPrimaryButton(confirmLabel);
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        confirmButton.addClickListener(_ -> {
            try {
                onConfirm.run();
            } finally {
                close();
            }
        });

        UiTertiaryButton cancelButton = new UiTertiaryButton(cancelLabel);
        cancelButton.addClickListener(_ -> close());

        getFooter().add(confirmButton, cancelButton);
    }
}
