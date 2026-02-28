package org.ost.advertisement.ui.views.header.dialogs;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SessionService;
import org.ost.advertisement.ui.views.services.NotificationService;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.fields.UiEmailField;
import org.ost.advertisement.ui.views.components.fields.UiPasswordField;
import org.ost.advertisement.ui.views.components.buttons.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.buttons.UiTertiaryButton;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class LoginDialog extends BaseDialog {

    private final transient AuthService              authService;
    private final transient I18nService              i18n;
    private final transient NotificationService      notificationService;
    private final transient SessionService           sessionService;
    private final           DialogLayout             layout;
    private final transient UiEmailField.Builder     emailFieldBuilder;
    private final transient UiPasswordField.Builder  passwordFieldBuilder;
    private final transient UiPrimaryButton.Builder  loginButtonBuilder;
    private final transient UiTertiaryButton.Builder cancelButtonBuilder;

    private UiEmailField    emailField;
    private UiPasswordField passwordField;

    @Override
    @PostConstruct
    protected void init() {
        super.init(layout);
        emailField = emailFieldBuilder.build(
                UiEmailField.Parameters.builder()
                        .labelKey(LOGIN_EMAIL_LABEL)
                        .placeholderKey(LOGIN_EMAIL_LABEL)
                        .required(true)
                        .build());
        passwordField = passwordFieldBuilder.build(
                UiPasswordField.Parameters.builder()
                        .labelKey(LOGIN_PASSWORD_LABEL)
                        .placeholderKey(LOGIN_PASSWORD_LABEL)
                        .required(true)
                        .build());
        setTitle();
        addContent();
        addActions();
    }

    private void setTitle() {
        setHeaderTitle(i18n.get(USER_DIALOG_TITLE));
    }

    private void addContent() {
        Paragraph welcome = new Paragraph(i18n.get(LOGIN_WELCOME));
        welcome.addClassName("dialog-subtitle");
        layout.addFormContent(welcome, emailField, passwordField);
    }

    private void addActions() {
        UiPrimaryButton loginButton = loginButtonBuilder.build(
                UiPrimaryButton.Parameters.builder().labelKey(LOGIN_BUTTON_SUBMIT).build());
        loginButton.addClickListener(_ -> handleLogin());

        UiTertiaryButton cancelButton = cancelButtonBuilder.build(
                UiTertiaryButton.Parameters.builder().labelKey(LOGIN_BUTTON_CANCEL).build());
        cancelButton.addClickListener(_ -> close());

        getFooter().add(loginButton, cancelButton);
    }

    private void handleLogin() {
        boolean success = authService.login(emailField.getValue(), passwordField.getValue());
        if (success) {
            close();
            notificationService.success(LOGIN_SUCCESS);
            sessionService.refreshCurrentLocale();
            UI.getCurrent().getPage().reload();
        } else {
            notificationService.error(LOGIN_ERROR);
        }
    }
}