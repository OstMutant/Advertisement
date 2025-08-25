package org.ost.advertisement.ui.views.header;

import static org.ost.advertisement.Constants.EMAIL_PATTERN;

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
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.utils.PasswordEncoderUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.validation.NewUserValidator;

@SpringComponent
@UIScope
public class SignUpDialog extends Dialog {
	private final UserRepository userRepository;
	private final I18nService i18n;

	public SignUpDialog(UserRepository userRepository, I18nService i18n) {
		this.userRepository = userRepository;
		this.i18n = i18n;

		setModal(true);
		setDraggable(false);
		setResizable(false);
		setHeaderTitle(i18n.get("signup.header.title"));

		TextField nameField = new TextField(i18n.get("signup.name.label"));
		EmailField emailField = new EmailField(i18n.get("signup.email.label"));
		PasswordField passwordField = new PasswordField(i18n.get("signup.password.label"));

		Button registerButton = new Button(i18n.get("signup.button.submit"));
		registerButton.addThemeName("primary");

		Button cancelButton = new Button(i18n.get("signup.button.cancel"), e -> close());
		cancelButton.addThemeName("tertiary");

		HorizontalLayout actions = new HorizontalLayout(registerButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

		FormLayout form = new FormLayout(nameField, emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		add(form);

		registerButton.addClickListener(event -> {
			String name = nameField.getValue().trim();
			String email = emailField.getValue().trim();
			String rawPassword = passwordField.getValue().trim();

			if (!validateFields(nameField, emailField, passwordField, name, email, rawPassword)) {
				return;
			}

			User newUser = new User();
			newUser.setName(name);
			newUser.setEmail(email);
			newUser.setPasswordHash(PasswordEncoderUtil.encode(rawPassword));
			newUser.setCreatedAt(Instant.now());
			newUser.setUpdatedAt(Instant.now());
			newUser.setRole(Role.USER);

			userRepository.save(newUser);
			Notification.show(i18n.get("signup.success"), 2000, Notification.Position.TOP_CENTER);
			close();
		});
	}

	private boolean validateFields(TextField nameField, EmailField emailField, PasswordField passwordField,
								   String name, String email, String password) {
		boolean valid = true;

		if (name.isEmpty()) {
			nameField.setInvalid(true);
			nameField.setErrorMessage(i18n.get("signup.error.name.required"));
			valid = false;
		} else {
			nameField.setInvalid(false);
		}

		if (!EMAIL_PATTERN.matcher(email).matches()) {
			emailField.setInvalid(true);
			emailField.setErrorMessage(i18n.get("signup.error.email.invalid"));
			valid = false;
		} else if (userRepository.findByEmail(email).isPresent()) {
			emailField.setInvalid(true);
			emailField.setErrorMessage(i18n.get("signup.error.email.exists"));
			valid = false;
		} else {
			emailField.setInvalid(false);
		}

		if (password.length() < 6) {
			passwordField.setInvalid(true);
			passwordField.setErrorMessage(i18n.get("signup.error.password.short"));
			valid = false;
		} else {
			passwordField.setInvalid(false);
		}

		return valid;
	}
}
