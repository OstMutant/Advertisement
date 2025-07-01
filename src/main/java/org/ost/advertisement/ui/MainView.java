package org.ost.advertisement.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;

@Route("")
public class MainView extends VerticalLayout {

	public MainView() {
		setAlignItems(Alignment.CENTER);
		setJustifyContentMode(JustifyContentMode.CENTER);

		H1 greeting = new H1("Hello, Vaadin world with Spring Boot!");
		Button button = new Button("Click me!");

		button.addClickListener(e -> {
			Notification.show("Greetings! You clicked the Vaadin button!");
		});

		add(greeting, button);
		getStyle().set("padding", "20px");
	}
}
