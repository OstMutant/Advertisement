package org.ost.advertisement.ui.views.header.dialogs;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.SessionService;
import org.ost.advertisement.ui.services.NotificationService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogPrimaryButton;
import org.ost.advertisement.ui.views.components.dialogs.fields.DialogTertiaryButton;
import org.ost.advertisement.ui.views.header.dialogs.fields.LoginEmailField;
import org.ost.advertisement.ui.views.header.dialogs.fields.LoginPasswordField;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class LoginDialog extends BaseDialog {

    private final transient AuthService authService;
    @Getter
    private final transient I18nService i18n;
    @Getter
    private final transient NotificationService notificationService;
    private final transient SessionService sessionService;

    private final LoginEmailField emailField;
    private final LoginPasswordField passwordField;
    @Getter
    private final DialogLayout layout;

    @Override
    @PostConstruct
    protected void init() {
        super.init();
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
        DialogPrimaryButton loginButton = new DialogPrimaryButton(DialogPrimaryButton.Parameters.builder()
                .i18n(i18n).labelKey(LOGIN_BUTTON_SUBMIT).build());
        loginButton.addClickListener(_ -> handleLogin());

        DialogTertiaryButton cancelButton = new DialogTertiaryButton(DialogTertiaryButton.Parameters.builder()
                .i18n(i18n).labelKey(LOGIN_BUTTON_CANCEL).build());
        cancelButton.addClickListener(_ -> close());

        getFooter().add(loginButton, cancelButton);
    }

    private void handleLogin() {
        boolean success = authService.login(emailField.getValue(), passwordField.getValue());
        if (success) {
            close();
            notificationService.show(NotificationType.SUCCESS, LOGIN_SUCCESS);
            sessionService.refreshCurrentLocale();
            UI.getCurrent().getPage().reload();
        } else {
            notificationService.show(NotificationType.ERROR, LOGIN_ERROR);
        }
    }
}