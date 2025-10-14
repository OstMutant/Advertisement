package org.ost.advertisement.ui.views.header;

import static org.ost.advertisement.Constants.EMAIL_PATTERN;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_BUTTON_CANCEL;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_BUTTON_SUBMIT;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_EMAIL_LABEL;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_ERROR_EMAIL_EXISTS;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_ERROR_EMAIL_INVALID;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_ERROR_NAME_REQUIRED;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_ERROR_PASSWORD_SHORT;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_HEADER_TITLE;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_NAME_LABEL;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_PASSWORD_LABEL;
import static org.ost.advertisement.constans.I18nKey.SIGNUP_SUCCESS;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.utils.PasswordEncoderUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;

@SpringComponent
@UIScope
public class SignUpDialog extends Dialog {

	private final transient UserRepository userRepository;
	private final transient I18nService i18n;

	public SignUpDialog(UserRepository userRepository, I18nService i18n) {
		this.userRepository = userRepository;
		this.i18n = i18n;

		setModal(true);
		setDraggable(false);
		setResizable(false);
		setHeaderTitle(i18n.get(SIGNUP_HEADER_TITLE));

		TextField nameField = DialogContentFactory.textField(i18n, SIGNUP_NAME_LABEL, SIGNUP_NAME_LABEL, 255, true);
		EmailField emailField = DialogContentFactory.emailField(i18n, SIGNUP_EMAIL_LABEL, SIGNUP_EMAIL_LABEL, true);
		PasswordField passwordField = DialogContentFactory.passwordField(i18n, SIGNUP_PASSWORD_LABEL,
			SIGNUP_PASSWORD_LABEL, true);

		Button registerButton = DialogContentFactory.primaryButton(i18n, SIGNUP_BUTTON_SUBMIT);
		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, SIGNUP_BUTTON_CANCEL);

		cancelButton.addClickListener(e -> close());
		registerButton.addClickListener(event -> handleRegistration(nameField, emailField, passwordField));

		HorizontalLayout actions = new HorizontalLayout(registerButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(JustifyContentMode.END);

		FormLayout form = new FormLayout(nameField, emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		add(form);
	}

	private void handleRegistration(TextField nameField, EmailField emailField, PasswordField passwordField) {
		String name = nameField.getValue().trim();
		String email = emailField.getValue().trim();
		String rawPassword = passwordField.getValue().trim();

		if (!validateFields(nameField, emailField, passwordField, name, email, rawPassword)) {
			return;
		}

		User newUser = User.builder()
			.name(name)
			.email(email)
			.passwordHash(PasswordEncoderUtil.encode(rawPassword))
			.role(Role.USER)
			.build();

		userRepository.save(newUser);
		NotificationType.SUCCESS.show(i18n.get(SIGNUP_SUCCESS));
		close();
	}

	private boolean validateFields(TextField nameField, EmailField emailField, PasswordField passwordField,
								   String name, String email, String password) {
		boolean valid = true;

		if (name.isEmpty()) {
			nameField.setInvalid(true);
			nameField.setErrorMessage(i18n.get(SIGNUP_ERROR_NAME_REQUIRED));
			valid = false;
		} else {
			nameField.setInvalid(false);
		}

		if (!EMAIL_PATTERN.matcher(email).matches()) {
			emailField.setInvalid(true);
			emailField.setErrorMessage(i18n.get(SIGNUP_ERROR_EMAIL_INVALID));
			valid = false;
		} else if (userRepository.findByEmail(email).isPresent()) {
			emailField.setInvalid(true);
			emailField.setErrorMessage(i18n.get(SIGNUP_ERROR_EMAIL_EXISTS));
			valid = false;
		} else {
			emailField.setInvalid(false);
		}

		if (password.length() < 6) {
			passwordField.setInvalid(true);
			passwordField.setErrorMessage(i18n.get(SIGNUP_ERROR_PASSWORD_SHORT));
			valid = false;
		} else {
			passwordField.setInvalid(false);
		}

		return valid;
	}
}


