package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ConfirmDeleteHelper {

	public static void showConfirm(I18nService i18n, String message, I18nKey confirmKey, I18nKey cancelKey,
								   Runnable onConfirm) {
		Dialog dialog = new Dialog();

		Button confirmButton = DialogContentFactory.primaryButton(i18n, confirmKey);
		confirmButton.addClickListener(event -> {
			try {
				onConfirm.run();
			} finally {
				dialog.close();
			}
		});

		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, cancelKey);
		cancelButton.addClickListener(e -> dialog.close());

		DialogLayout layout = new DialogLayout();
		layout.setHeader(message);
		layout.addActions(confirmButton, cancelButton);

		dialog.add(layout.getLayout());
		dialog.open();
	}
}
