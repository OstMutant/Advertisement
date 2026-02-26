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
import org.ost.advertisement.ui.views.components.dialogs.BaseDialog;
import org.ost.advertisement.ui.views.components.dialogs.DialogLayout;
import org.ost.advertisement.ui.views.components.fields.UiEmailField;
import org.ost.advertisement.ui.views.components.fields.UiPasswordField;
import org.ost.advertisement.ui.views.components.fields.UiPrimaryButton;
import org.ost.advertisement.ui.views.components.fields.UiTertiaryButton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import static org.ost.advertisement.constants.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class LoginDialog extends BaseDialog {

    private final transient AuthService         authService;
    @Getter
    private final transient I18nService         i18n;
    @Getter
    private final transient NotificationService notificationService;
    private final transient SessionService      sessionService;

    @Getter
    private final DialogLayout                  layout;
    private final transient ObjectProvider<UiEmailField>    emailFieldProvider;
    private final transient ObjectProvider<UiPasswordField> passwordFieldProvider;
    private final transient ObjectProvider<UiPrimaryButton>  loginButtonProvider;
    private final transient ObjectProvider<UiTertiaryButton> cancelButtonProvider;

    private UiEmailField uiEmailField;
    private UiPasswordField uiPasswordField;

    @Override
    @PostConstruct
    protected void init() {
        super.init();
        uiEmailField = emailFieldProvider.getObject().configure(
                UiEmailField.Parameters.builder()
                        .labelKey(LOGIN_EMAIL_LABEL)
                        .placeholderKey(LOGIN_EMAIL_LABEL)
                        .required(true)
                        .build());
        uiPasswordField = passwordFieldProvider.getObject().configure(
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
        layout.addFormContent(welcome, uiEmailField, uiPasswordField);
    }

    private void addActions() {
        UiPrimaryButton loginButton = loginButtonProvider.getObject().configure(
                UiPrimaryButton.Parameters.builder().labelKey(LOGIN_BUTTON_SUBMIT).build());
        loginButton.addClickListener(_ -> handleLogin());

        UiTertiaryButton cancelButton = cancelButtonProvider.getObject().configure(
                UiTertiaryButton.Parameters.builder().labelKey(LOGIN_BUTTON_CANCEL).build());
        cancelButton.addClickListener(_ -> close());

        getFooter().add(loginButton, cancelButton);
    }

    private void handleLogin() {
        boolean success = authService.login(uiEmailField.getValue(), uiPasswordField.getValue());
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