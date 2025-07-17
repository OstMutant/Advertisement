package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;

@SpringComponent
@UIScope
public class LoginDialog extends Dialog {

	public LoginDialog(AuthService authService) {
		setHeaderTitle("Sign In");

		EmailField emailField = new EmailField("Email");
		PasswordField passwordField = new PasswordField("Password");

		Button loginButton = new Button("Log In");
		Button cancelButton = new Button("Cancel", e -> close());

		loginButton.addClickListener(event -> {
			String email = emailField.getValue();
			String password = passwordField.getValue();

			boolean success = authService.login(email, password);

			if (success) {
				close();
				Notification.show("Logged in successfully", 2000, Notification.Position.TOP_CENTER);
				UI.getCurrent().getPage().reload();
			} else {
				Notification.show("Invalid email or password", 3000, Notification.Position.MIDDLE);
			}
		});

		loginButton.addThemeName("primary");
		cancelButton.addThemeName("tertiary");

		HorizontalLayout actions = new HorizontalLayout(loginButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

		FormLayout form = new FormLayout(emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1)); // одна колонка

		add(new H2("Welcome"), form);
		setModal(true);
		setDraggable(false);
		setResizable(false);
	}
}
