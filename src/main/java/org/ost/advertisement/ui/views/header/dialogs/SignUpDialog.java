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
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.fields.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.Constants.EMAIL_PATTERN;
import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SignUpDialog extends BaseDialog {

    private final transient UserService userService;
    @Getter
    private final transient I18nService i18n;
    @Getter
    private final transient NotificationService notificationService;

    @Getter
    private final DialogLayout layout;
    private final transient ObjectProvider<UiTextField> nameFieldProvider;
    private final transient ObjectProvider<UiEmailField> emailFieldProvider;
    private final transient ObjectProvider<UiPasswordField> passwordFieldProvider;
    private final transient ObjectProvider<UiPrimaryButton> registerButtonProvider;
    private final transient ObjectProvider<UiTertiaryButton> cancelButtonProvider;

    private UiTextField nameField;
    private UiEmailField uiEmailField;
    private UiPasswordField uiPasswordField;

    private final Binder<SignUpDto> binder = new Binder<>(SignUpDto.class);
    private final transient SignUpDto dto = new SignUpDto();

    @Override
    @PostConstruct
    protected void init() {
        super.init();
        addThemeName("signup-dialog");

        nameField = nameFieldProvider.getObject().configure(
                UiTextField.Parameters.builder()
                        .labelKey(SIGNUP_NAME_LABEL)
                        .placeholderKey(SIGNUP_NAME_LABEL)
                        .maxLength(255)
                        .required(true)
                        .build());
        uiEmailField = emailFieldProvider.getObject().configure(
                UiEmailField.Parameters.builder()
                        .labelKey(SIGNUP_EMAIL_LABEL)
                        .placeholderKey(SIGNUP_EMAIL_LABEL)
                        .required(true)
                        .build());
        uiPasswordField = passwordFieldProvider.getObject().configure(
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
        layout.addFormContent(nameField, uiEmailField, uiPasswordField);
    }

    private void addActions() {
        UiPrimaryButton registerButton = registerButtonProvider.getObject().configure(
                UiPrimaryButton.Parameters.builder().labelKey(SIGNUP_BUTTON_SUBMIT).build());
        UiTertiaryButton cancelButton = cancelButtonProvider.getObject().configure(
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

        binder.forField(uiEmailField)
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

        binder.forField(uiPasswordField)
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