package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentFactory {

	public static Button createSvgButton(String svgPath, String tooltip, ButtonVariant variant) {
		Button button = new Button(new SvgIcon("icons/" + svgPath));
		button.setText("");
		button.addThemeVariants(variant, ButtonVariant.LUMO_ICON);
		button.getElement().setProperty("title", tooltip);
		return button;
	}
}
