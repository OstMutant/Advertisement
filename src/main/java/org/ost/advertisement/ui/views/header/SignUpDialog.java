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
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.repository.user.UserRepository;
import org.ost.advertisement.security.utils.PasswordEncoderUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.DialogStyle;

@Slf4j
@SpringComponent
@UIScope
public class SignUpDialog extends Dialog {

	private final transient UserRepository userRepository;
	private final transient I18nService i18n;

	private final Binder<SignUpDto> binder = new Binder<>(SignUpDto.class);
	private final SignUpDto dto = new SignUpDto();

	public SignUpDialog(UserRepository userRepository, I18nService i18n) {
		this.userRepository = userRepository;
		this.i18n = i18n;

		DialogStyle.apply(this, i18n.get(SIGNUP_HEADER_TITLE));

		TextField nameField = DialogContentFactory.textField(i18n, SIGNUP_NAME_LABEL, SIGNUP_NAME_LABEL, 255, true);
		EmailField emailField = DialogContentFactory.emailField(i18n, SIGNUP_EMAIL_LABEL, SIGNUP_EMAIL_LABEL, true);
		PasswordField passwordField = DialogContentFactory.passwordField(i18n, SIGNUP_PASSWORD_LABEL,
			SIGNUP_PASSWORD_LABEL, true);

		Button registerButton = DialogContentFactory.primaryButton(i18n, SIGNUP_BUTTON_SUBMIT);
		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, SIGNUP_BUTTON_CANCEL);

		cancelButton.addClickListener(e -> close());
		registerButton.addClickListener(event -> handleRegistration());

		DialogLayout layout = new DialogLayout();
		layout.setHeader(i18n.get(SIGNUP_HEADER_TITLE));
		layout.addFormContent(nameField, emailField, passwordField);
		layout.addActions(registerButton, cancelButton);
		add(layout.getLayout());

		setupBinder(nameField, emailField, passwordField);
	}

	private void setupBinder(TextField nameField, EmailField emailField, PasswordField passwordField) {
		binder.setBean(dto);

		binder.forField(nameField)
			.withValidator(new StringLengthValidator(i18n.get(SIGNUP_ERROR_NAME_REQUIRED), 1, 255))
			.bind(SignUpDto::getName, SignUpDto::setName);

		binder.forField(emailField)
			.withValidator(email -> EMAIL_PATTERN.matcher(email == null ? "" : email.trim()).matches(),
				i18n.get(SIGNUP_ERROR_EMAIL_INVALID))
			.withValidator(email -> email != null && userRepository.findByEmail(email.trim()).isEmpty(),
				i18n.get(SIGNUP_ERROR_EMAIL_EXISTS))
			.bind(SignUpDto::getEmail, SignUpDto::setEmail);

		binder.forField(passwordField)
			.withValidator(new StringLengthValidator(i18n.get(SIGNUP_ERROR_PASSWORD_SHORT), 6, 255))
			.bind(SignUpDto::getPassword, SignUpDto::setPassword);
		
	}

	private void handleRegistration() {
		try {
			binder.writeBean(dto);

			User newUser = User.builder()
				.name(dto.getName().trim())
				.email(dto.getEmail().trim())
				.passwordHash(PasswordEncoderUtil.encode(dto.getPassword().trim()))
				.role(Role.USER)
				.build();

			userRepository.save(newUser);
			NotificationType.SUCCESS.show(i18n.get(SIGNUP_SUCCESS));
			close();
		} catch (ValidationException ex) {
			log.warn("SignUp validation failed: {}", ex.getMessage());
		}
	}

	@Data
	public static class SignUpDto {

		private String name;
		private String email;
		private String password;
	}
}
