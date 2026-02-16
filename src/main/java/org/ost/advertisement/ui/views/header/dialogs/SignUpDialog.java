package org.ost.advertisement.ui.views.header.dialogs;

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.binder.ValidationException;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.SignUpDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.DialogStyle;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;
import org.ost.advertisement.ui.views.header.dialogs.fields.SignUpEmailField;
import org.ost.advertisement.ui.views.header.dialogs.fields.SignUpNameField;
import org.ost.advertisement.ui.views.header.dialogs.fields.SignUpPasswordField;

import static org.ost.advertisement.Constants.EMAIL_PATTERN;
import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@UIScope
public class SignUpDialog extends Dialog {

    private final transient UserService userService;
    private final transient I18nService i18n;

    private final SignUpNameField nameField;
    private final SignUpEmailField emailField;
    private final SignUpPasswordField passwordField;

    private final Binder<SignUpDto> binder = new Binder<>(SignUpDto.class);
    private final transient SignUpDto dto = new SignUpDto();

    public SignUpDialog(UserService userService,
                        I18nService i18n,
                        SignUpNameField nameField,
                        SignUpEmailField emailField,
                        SignUpPasswordField passwordField) {
        this.userService = userService;
        this.i18n = i18n;
        this.nameField = nameField;
        this.emailField = emailField;
        this.passwordField = passwordField;

        DialogStyle.apply(this, i18n.get(SIGNUP_HEADER_TITLE));

        DialogPrimaryButton registerButton = new DialogPrimaryButton(DialogPrimaryButton.Parameters.builder()
                .i18n(i18n).labelKey(SIGNUP_BUTTON_SUBMIT).build());
        DialogTertiaryButton cancelButton = new DialogTertiaryButton(DialogTertiaryButton.Parameters.builder()
                .i18n(i18n).labelKey(SIGNUP_BUTTON_CANCEL).build());

        cancelButton.addClickListener(_ -> close());
        registerButton.addClickListener(_ -> handleRegistration());

        DialogLayout layout = new DialogLayout();
        layout.addFormContent(nameField, emailField, passwordField);
        layout.addActions(registerButton, cancelButton);
        add(layout.getLayout());

        setupBinder();
    }

    private void setupBinder() {
        binder.setBean(dto);

        binder.forField(nameField)
                .withValidator(new StringLengthValidator(i18n.get(SIGNUP_ERROR_NAME_REQUIRED), 1, 255))
                .bind(SignUpDto::getName, SignUpDto::setName);

        binder.forField(emailField)
                .withValidator(email -> EMAIL_PATTERN.matcher(email == null ? "" : email.trim()).matches(),
                        i18n.get(SIGNUP_ERROR_EMAIL_INVALID))
                .withValidator(email -> {
                    try {
                        return email != null && userService.findByEmail(email.trim()).isEmpty();
                    } catch (Exception e) {
                        log.warn("Failed to check email uniqueness", e);
                        return false;
                    }
                }, i18n.get(SIGNUP_ERROR_EMAIL_EXISTS))
                .bind(SignUpDto::getEmail, SignUpDto::setEmail);

        binder.forField(passwordField)
                .withValidator(new StringLengthValidator(i18n.get(SIGNUP_ERROR_PASSWORD_SHORT), 6, 255))
                .bind(SignUpDto::getPassword, SignUpDto::setPassword);
    }

    private void handleRegistration() {
        try {
            binder.writeBean(dto);
            userService.register(dto);
            NotificationType.SUCCESS.show(i18n.get(SIGNUP_SUCCESS));
            close();
        } catch (ValidationException ex) {
            log.warn("SignUp validation failed: {}", ex.getMessage());
        }
    }
}