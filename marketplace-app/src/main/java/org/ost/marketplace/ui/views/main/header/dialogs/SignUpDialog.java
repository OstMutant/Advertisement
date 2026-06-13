package org.ost.marketplace.ui.views.main.header.dialogs;

import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.user.dto.SignUpDto;
import org.ost.platform.core.i18n.I18nService;
import org.ost.user.services.UserService;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.components.dialogs.BaseDialog;
import org.ost.marketplace.ui.views.components.dialogs.DialogLayout;
import org.ost.marketplace.ui.views.components.fields.UiEmailField;
import org.ost.marketplace.ui.views.components.fields.UiPasswordField;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.views.components.fields.UiTextField;
import org.ost.platform.core.ComponentFactory;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.common.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class SignUpDialog extends BaseDialog implements I18nParams {

    private final transient UserService                             userService;
    @Getter
    private final transient I18nService                             i18nService;
    private final transient NotificationService                     notificationService;
    private final           DialogLayout                            layout;
    private final transient ComponentFactory<UiTextField>           textFieldFactory;
    private final transient ComponentFactory<UiEmailField>          emailFieldFactory;
    private final transient ComponentFactory<UiPasswordField>       passwordFieldFactory;
    private final transient ComponentFactory<UiPrimaryButton>       primaryButtonFactory;
    private final transient ComponentFactory<UiTertiaryButton>      tertiaryButtonFactory;

    private UiTextField     nameField;
    private UiEmailField    emailField;
    private UiPasswordField passwordField;

    private final BeanValidationBinder<SignUpDto> binder = new BeanValidationBinder<>(SignUpDto.class);
    private final transient SignUpDto dto    = new SignUpDto();

    @Override
    @PostConstruct
    protected void buildLayout() {
        super.buildLayout(layout);
        addThemeName("signup-dialog");

        nameField = textFieldFactory.build(
                UiTextField.Parameters.builder()
                        .labelKey(SIGNUP_NAME_LABEL)
                        .placeholderKey(SIGNUP_NAME_LABEL)
                        .maxLength(255)
                        .required(true)
                        .build());
        emailField = emailFieldFactory.build(
                UiEmailField.Parameters.builder()
                        .labelKey(SIGNUP_EMAIL_LABEL)
                        .placeholderKey(SIGNUP_EMAIL_LABEL)
                        .required(true)
                        .build());
        passwordField = passwordFieldFactory.build(
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
        setHeaderTitle(getValue(SIGNUP_HEADER_TITLE));
    }

    private void addContent() {
        layout.addFormContent(nameField, emailField, passwordField);
    }

    private void addActions() {
        UiPrimaryButton registerButton = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(SIGNUP_BUTTON_SUBMIT).build());
        UiTertiaryButton cancelButton = tertiaryButtonFactory.build(
                UiTertiaryButton.Parameters.builder().labelKey(SIGNUP_BUTTON_CANCEL).build());

        cancelButton.addClickListener(_ -> close());
        registerButton.addClickListener(_ -> handleRegistration());

        getFooter().add(registerButton, cancelButton);
    }

    private void bindFields() {
        binder.setBean(dto);

        binder.forField(nameField).bind(SignUpDto::getName, SignUpDto::setName);

        binder.forField(emailField)
                .withValidator(email -> {
                    try {
                        return email != null && userService.findByEmail(email.trim()).isEmpty();
                    } catch (Exception e) {
                        log.warn("Failed to check email uniqueness", e);
                        return false;
                    }
                }, getValue(SIGNUP_ERROR_EMAIL_EXISTS))
                .bind(SignUpDto::getEmail, SignUpDto::setEmail);

        binder.forField(passwordField).bind(SignUpDto::getPassword, SignUpDto::setPassword);
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
