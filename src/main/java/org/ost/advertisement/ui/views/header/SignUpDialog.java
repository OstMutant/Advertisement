package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.time.Instant;
import java.util.regex.Pattern;
import org.ost.advertisement.entyties.User;
import org.ost.advertisement.repository.UserRepository;
import org.ost.advertisement.utils.PasswordEncoderUtil;

@SpringComponent
@UIScope
public class SignUpDialog extends Dialog {

	private final Pattern emailPattern = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	public SignUpDialog(UserRepository userRepository) {
		setHeaderTitle("Register New User");

		TextField nameField = new TextField("Name");
		EmailField emailField = new EmailField("Email");
		PasswordField passwordField = new PasswordField("Password");

		Button registerButton = new Button("Sign Up");
		Button cancelButton = new Button("Cancel", e -> close());

		registerButton.addClickListener(event -> {
			String name = nameField.getValue().trim();
			String email = emailField.getValue().trim();
			String rawPassword = passwordField.getValue().trim();

			boolean valid = validateFields(nameField, emailField, passwordField, name, email, rawPassword, userRepository);
			if (!valid) return;

			User newUser = new User();
			newUser.setName(name);
			newUser.setEmail(email);
			newUser.setPasswordHash(PasswordEncoderUtil.encode(rawPassword));
			newUser.setCreatedAt(Instant.now());
			newUser.setUpdatedAt(Instant.now());

			userRepository.save(newUser);
			Notification.show("User registered successfully", 2000, Notification.Position.TOP_CENTER);
			close();
		});

		registerButton.addThemeName("primary");
		cancelButton.addThemeName("tertiary");

		HorizontalLayout actions = new HorizontalLayout(registerButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

		FormLayout form = new FormLayout(nameField, emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1)); // одна колонка

		add(form);
		setModal(true);
		setDraggable(false);
		setResizable(false);
	}

	private boolean validateFields(TextField nameField, EmailField emailField, PasswordField passwordField,
								   String name, String email, String password,
								   UserRepository userRepository) {
		boolean valid = true;

		if (name.isEmpty()) {
			nameField.setInvalid(true);
			nameField.setErrorMessage("Name is required");
			valid = false;
		} else {
			nameField.setInvalid(false);
		}

		if (!emailPattern.matcher(email).matches()) {
			emailField.setInvalid(true);
			emailField.setErrorMessage("Enter a valid email address");
			valid = false;
		} else if (userRepository.findByEmail(email).isPresent()) {
			emailField.setInvalid(true);
			emailField.setErrorMessage("Email already registered");
			valid = false;
		} else {
			emailField.setInvalid(false);
		}

		if (password.length() < 6) {
			passwordField.setInvalid(true);
			passwordField.setErrorMessage("Password must be at least 6 characters");
			valid = false;
		} else {
			passwordField.setInvalid(false);
		}

		return valid;
	}
}
