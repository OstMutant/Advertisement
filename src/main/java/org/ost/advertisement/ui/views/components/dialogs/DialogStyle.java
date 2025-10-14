package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.dialog.Dialog;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DialogStyle {

	public static void apply(Dialog dialog, String titleText) {
		dialog.setModal(true);
		dialog.setDraggable(false);
		dialog.setResizable(false);
		dialog.setHeaderTitle(titleText);
	}
}
