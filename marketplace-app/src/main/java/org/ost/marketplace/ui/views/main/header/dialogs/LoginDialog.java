package org.ost.marketplace.ui.views.main.header.dialogs;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.auth.AuthService;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.components.dialogs.BaseDialog;
import org.ost.marketplace.ui.views.components.dialogs.DialogLayout;
import org.ost.marketplace.ui.views.components.fields.UiEmailField;
import org.ost.marketplace.ui.views.components.fields.UiPasswordField;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class LoginDialog extends BaseDialog implements I18nParams {

    private final AuthService                            authService;
    @Getter
    private final I18nService                            i18nService;
    private final NotificationService                    notificationService;
    private final LocaleProvider                         localeProvider;
    private final DialogLayout                           layout;
    private final transient UiComponentFactory<UiEmailField>      emailFieldFactory;
    private final transient UiComponentFactory<UiPasswordField>   passwordFieldFactory;

    private UiEmailField    emailField;
    private UiPasswordField passwordField;

    @Override
    @PostConstruct
    protected void buildLayout() {
        super.buildLayout(layout);
        emailField = emailFieldFactory.build(
                UiEmailField.Parameters.builder()
                        .labelKey(LOGIN_EMAIL_LABEL)
                        .placeholderKey(LOGIN_EMAIL_LABEL)
                        .required(true)
                        .build());
        passwordField = passwordFieldFactory.build(
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
        setHeaderTitle(getValue(LOGIN_HEADER_TITLE));
    }

    private void addContent() {
        Paragraph welcome = new Paragraph(getValue(LOGIN_WELCOME));
        welcome.addClassName("dialog-subtitle");
        layout.addFormContent(welcome, emailField, passwordField);
    }

    private void addActions() {
        UiPrimaryButton loginButton = new UiPrimaryButton(getValue(LOGIN_BUTTON_SUBMIT));
        loginButton.addClickListener(_ -> handleLogin());

        UiTertiaryButton cancelButton = new UiTertiaryButton(getValue(LOGIN_BUTTON_CANCEL));
        cancelButton.addClickListener(_ -> close());

        getFooter().add(loginButton, cancelButton);
    }

    private void handleLogin() {
        try {
            boolean success = authService.login(emailField.getValue(), passwordField.getValue());
            if (success) {
                close();
                notificationService.success(LOGIN_SUCCESS);
                localeProvider.refreshCurrentLocale();
                getUI().ifPresent(ui -> ui.getPage().reload());
            } else {
                notificationService.error(LOGIN_ERROR);
            }
        } catch (IllegalStateException _) {
            notificationService.error(LOGIN_ERROR_TOO_MANY_ATTEMPTS);
        } catch (Exception ex) {
            log.error("Login failed unexpectedly", ex);
            notificationService.error(LOGIN_ERROR);
        }
    }
}
