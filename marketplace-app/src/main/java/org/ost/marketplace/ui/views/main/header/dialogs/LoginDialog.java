package org.ost.marketplace.ui.views.main.header.dialogs;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.auth.AuthService;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.LocaleProvider;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.rules.I18nParams;
import org.ost.marketplace.ui.views.components.dialogs.BaseDialog;
import org.ost.marketplace.ui.views.components.dialogs.DialogLayout;
import org.ost.marketplace.ui.views.components.fields.UiEmailField;
import org.ost.marketplace.ui.views.components.fields.UiPasswordField;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.springframework.context.annotation.Scope;

import static org.ost.marketplace.common.I18nKey.*;

@Slf4j
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class LoginDialog extends BaseDialog implements I18nParams {

    private final AuthService              authService;
    @Getter
    private final I18nService              i18nService;
    private final NotificationService      notificationService;
    private final LocaleProvider            localeProvider;
    private final           DialogLayout             layout;
    private final UiEmailField.Builder     emailFieldBuilder;
    private final UiPasswordField.Builder  passwordFieldBuilder;
    private final UiPrimaryButton.Builder  loginButtonBuilder;
    private final UiTertiaryButton.Builder cancelButtonBuilder;

    private UiEmailField    emailField;
    private UiPasswordField passwordField;

    @Override
    @PostConstruct
    protected void buildLayout() {
        super.buildLayout(layout);
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
        setHeaderTitle(getValue(LOGIN_HEADER_TITLE));
    }

    private void addContent() {
        Paragraph welcome = new Paragraph(getValue(LOGIN_WELCOME));
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
            localeProvider.refreshCurrentLocale();
            UI.getCurrent().getPage().reload();
        } else {
            notificationService.error(LOGIN_ERROR);
        }
    }
}
