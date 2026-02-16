package org.ost.advertisement.ui.views.header.dialogs;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SessionService;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.DialogStyle;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
public class LoginDialog extends Dialog {

    private final transient AuthService authService;
    private final transient I18nService i18n;
    private final transient SessionService sessionService;

    private EmailField emailField;
    private PasswordField passwordField;

    public LoginDialog(AuthService authService, I18nService i18n, SessionService sessionService) {
        this.authService = authService;
        this.i18n = i18n;
        this.sessionService = sessionService;

        DialogStyle.apply(this, i18n.get(LOGIN_HEADER_TITLE));
        initFields();
        initLayout();
    }

    private void initFields() {
        emailField = DialogContentFactory.emailField(i18n, LOGIN_EMAIL_LABEL, LOGIN_EMAIL_LABEL, true);
        passwordField = DialogContentFactory.passwordField(i18n, LOGIN_PASSWORD_LABEL, LOGIN_PASSWORD_LABEL, true);
    }

    private void initLayout() {
        Button loginButton = DialogContentFactory.primaryButton(i18n, LOGIN_BUTTON_SUBMIT);
        Button cancelButton = DialogContentFactory.tertiaryButton(i18n, LOGIN_BUTTON_CANCEL);

        loginButton.addClickListener(event -> handleLogin());
        cancelButton.addClickListener(event -> close());

        Paragraph welcome = new Paragraph(i18n.get(LOGIN_WELCOME));
        welcome.addClassName("dialog-subtitle");

        DialogLayout layout = new DialogLayout();
        layout.addFormContent(welcome, emailField, passwordField);
        layout.addActions(loginButton, cancelButton);

        add(layout.getLayout());
    }

    private void handleLogin() {
        boolean success = authService.login(emailField.getValue(), passwordField.getValue());

        if (success) {
            close();
            DialogContentFactory.showSuccess(i18n, LOGIN_SUCCESS);
            sessionService.refreshCurrentLocale();
            UI.getCurrent().getPage().reload();
        } else {
            DialogContentFactory.showError(i18n, LOGIN_ERROR);
        }
    }
}