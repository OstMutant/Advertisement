package org.ost.advertisement.ui.views.header.dialogs;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SessionService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.DialogStyle;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;
import org.ost.advertisement.ui.views.header.dialogs.fields.LoginEmailField;
import org.ost.advertisement.ui.views.header.dialogs.fields.LoginPasswordField;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
public class LoginDialog extends Dialog {

    private final transient AuthService authService;
    private final transient I18nService i18n;
    private final transient SessionService sessionService;

    private final LoginEmailField emailField;
    private final LoginPasswordField passwordField;

    public LoginDialog(AuthService authService,
                       I18nService i18n,
                       SessionService sessionService,
                       LoginEmailField emailField,
                       LoginPasswordField passwordField) {
        this.authService = authService;
        this.i18n = i18n;
        this.sessionService = sessionService;
        this.emailField = emailField;
        this.passwordField = passwordField;

        DialogStyle.apply(this, i18n.get(LOGIN_HEADER_TITLE));
        initLayout();
    }

    private void initLayout() {
        DialogPrimaryButton loginButton = new DialogPrimaryButton(DialogPrimaryButton.Parameters.builder()
                .i18n(i18n).labelKey(LOGIN_BUTTON_SUBMIT).build());
        DialogTertiaryButton cancelButton = new DialogTertiaryButton(DialogTertiaryButton.Parameters.builder()
                .i18n(i18n).labelKey(LOGIN_BUTTON_CANCEL).build());

        loginButton.addClickListener(_ -> handleLogin());
        cancelButton.addClickListener(_ -> close());

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
            NotificationType.SUCCESS.show(i18n.get(LOGIN_SUCCESS));
            sessionService.refreshCurrentLocale();
            UI.getCurrent().getPage().reload();
        } else {
            NotificationType.ERROR.show(i18n.get(LOGIN_ERROR));
        }
    }
}