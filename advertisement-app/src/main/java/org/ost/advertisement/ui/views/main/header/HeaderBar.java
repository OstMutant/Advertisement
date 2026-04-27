package org.ost.advertisement.ui.views.main.header;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.ui.views.main.header.dialogs.LoginDialog;
import org.ost.advertisement.ui.views.main.header.dialogs.LogoutDialog;
import org.ost.advertisement.ui.views.main.header.dialogs.SignUpDialog;
import org.ost.advertisement.ui.views.main.header.settings.SettingsOverlay;

import static org.ost.advertisement.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class HeaderBar extends HorizontalLayout {

    private final LocaleSelectorComponent localeSelectorComponent;
    private final LoginDialog             loginDialog;
    private final LogoutDialog            logoutDialog;
    private final SignUpDialog            signUpDialog;
    private final SettingsOverlay         settingsOverlay;
    private final transient I18nService          i18n;
    private final transient AuthContextService   authContextService;

    @PostConstruct
    protected void init() {
        addClassName("header-bar");
        add(settingsOverlay);
        add(buildLogo());
        add(initAuthBlock());
    }

    private Div buildLogo() {
        Image icon = new Image("icons/logo.svg", "Logo");
        icon.addClassName("header-logo-icon");

        Div logo = new Div(icon);
        logo.addClassName("header-logo");
        return logo;
    }

    private VerticalLayout initAuthBlock() {
        VerticalLayout authBlock = new VerticalLayout();
        authBlock.addClassName("header-auth-block");

        authBlock.add(initLocaleRow(), initUserInfoRow());
        return authBlock;
    }

    private HorizontalLayout initLocaleRow() {
        HorizontalLayout localeRow = new HorizontalLayout(localeSelectorComponent);
        localeRow.addClassName("header-locale-row");
        return localeRow;
    }

    private HorizontalLayout initUserInfoRow() {
        HorizontalLayout authRow = new HorizontalLayout();
        authRow.addClassName("header-auth-row");

        Span userInfo = new Span();
        User currentUser = authContextService.getCurrentUser().orElse(null);

        if (currentUser != null) {
            userInfo.setText(i18n.get(HEADER_SIGNED_IN, currentUser.getEmail()));
            authRow.add(userInfo, createSettingsButton(), createLogoutButton());
        } else {
            userInfo.setText(i18n.get(HEADER_NOT_SIGNED_IN));
            authRow.add(userInfo, createLoginButton(), createSignUpButton());
        }

        return authRow;
    }

    private Button createSettingsButton() {
        Button button = new Button(i18n.get(HEADER_SETTINGS), VaadinIcon.COG.create(),
                _ -> settingsOverlay.openSettings());
        button.addClassName("header-settings-button");
        return button;
    }

    private Button createLoginButton() {
        Button loginButton = new Button(i18n.get(HEADER_LOGIN), VaadinIcon.SIGN_IN.create(),
                _ -> loginDialog.open());
        loginButton.addClassName("header-login-button");
        return loginButton;
    }

    private Button createSignUpButton() {
        Button signUpButton = new Button(i18n.get(HEADER_SIGNUP), VaadinIcon.USER.create(),
                _ -> signUpDialog.open());
        signUpButton.addClassName("header-signup-button");
        return signUpButton;
    }

    private Button createLogoutButton() {
        Button logoutButton = new Button(i18n.get(HEADER_LOGOUT), VaadinIcon.SIGN_OUT.create(),
                _ -> logoutDialog.open());
        logoutButton.addClassName("header-logout-button");
        return logoutButton;
    }
}
