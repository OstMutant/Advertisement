package org.ost.advertisement.ui.views.advertisements;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class AdvertisementDescriptionDialog extends Dialog {

	public AdvertisementDescriptionDialog(String title, String description) {
		setWidth("600px");
		setCloseOnEsc(true);
		setCloseOnOutsideClick(true);

		H3 heading = new H3(title);
		Span content = new Span(description);
		content.getStyle()
			.set("white-space", "pre-wrap")
			.set("line-height", "1.5")
			.set("font-size", "0.95rem")
			.set("color", "#444");

		Button close = new Button("Close", e -> close());
		close.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		VerticalLayout layout = new VerticalLayout(heading, content, close);
		layout.setPadding(true);
		layout.setSpacing(true);
		add(layout);
	}
}

