package org.ost.advertisement.ui.views.header.dialogs;

import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.dto.SignUpDto;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.fields.UiEmailField;
import org.ost.advertisement.ui.views.components.fields.UiPasswordField;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.buttons.UiTertiaryButton;
import org.ost.advertisement.ui.views.components.fields.UiTextField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.Constants.EMAIL_PATTERN;
import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SignUpDialog extends BaseDialog {

    private final transient UserService         userService;
    @Getter
    private final transient I18nService         i18n;
    @Getter
    private final transient NotificationService notificationService;

    @Getter
    private final DialogLayout              layout;
    private final transient UiTextField.Builder       nameFieldBuilder;
    private final transient UiEmailField.Builder      emailFieldBuilder;
    private final transient UiPasswordField.Builder   passwordFieldBuilder;
    private final transient UiPrimaryButton.Builder   registerButtonBuilder;
    private final transient UiTertiaryButton.Builder  cancelButtonBuilder;

    private UiTextField     nameField;
    private UiEmailField    emailField;
    private UiPasswordField passwordField;

    private final Binder<SignUpDto>   binder = new Binder<>(SignUpDto.class);
    private final transient SignUpDto dto    = new SignUpDto();

    @Override
    @PostConstruct
    protected void init() {
        super.init();
        addThemeName("signup-dialog");

        nameField = nameFieldBuilder.build(
                UiTextField.Parameters.builder()
                        .labelKey(SIGNUP_NAME_LABEL)
                        .placeholderKey(SIGNUP_NAME_LABEL)
                        .maxLength(255)
                        .required(true)
                        .build());
        emailField = emailFieldBuilder.build(
                UiEmailField.Parameters.builder()
                        .labelKey(SIGNUP_EMAIL_LABEL)
                        .placeholderKey(SIGNUP_EMAIL_LABEL)
                        .required(true)
                        .build());
        passwordField = passwordFieldBuilder.build(
                UiPasswordField.Parameters.builder()
                        .labelKey(SIGNUP_PASSWORD_LABEL)
                        .placeholderKey(SIGNUP_PASSWORD_LABEL)
                        .required(true)
                        .build());

        setTitle();
        addContent();
        bindFields();
        addActions();
    }

    private void setTitle() {
        setHeaderTitle(i18n.get(SIGNUP_HEADER_TITLE));
    }

    private void addContent() {
        layout.addFormContent(nameField, emailField, passwordField);
    }

    private void addActions() {
        UiPrimaryButton registerButton = registerButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(SIGNUP_BUTTON_SUBMIT).build());
        UiTertiaryButton cancelButton = cancelButtonBuilder.build(
                UiTertiaryButton.Parameters.builder().labelKey(SIGNUP_BUTTON_CANCEL).build());

        cancelButton.addClickListener(_ -> close());
        registerButton.addClickListener(_ -> handleRegistration());

        getFooter().add(registerButton, cancelButton);
    }

    private void bindFields() {
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
        if (!binder.validate().isOk()) {
            return;
        }
        try {
            binder.writeBean(dto);
            userService.register(dto);
            notificationService.success(SIGNUP_SUCCESS);
            close();
        } catch (Exception ex) {
            log.error("Registration failed unexpectedly", ex);
            notificationService.error(SIGNUP_ERROR_EMAIL_EXISTS);
        }
    }
}
