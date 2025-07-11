package org.ost.advertisement.ui.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

public class FloatingActionButton extends Button {

	public FloatingActionButton(VaadinIcon icon, String tooltip, ComponentEventListener<ClickEvent<Button>> listener) {
		super(icon.create());
		setText("");
		addClickListener(listener);
		getElement().setProperty("title", tooltip);
		addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ICON);

		getStyle()
			.set("position", "fixed")
			.set("bottom", "32px")
			.set("right", "32px")
			.set("z-index", "1000")
			.set("border-radius", "50%")
			.set("width", "48px")
			.set("height", "48px")
			.set("box-shadow", "0 2px 6px rgba(0,0,0,0.3)");
	}
}
