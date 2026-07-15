package org.ost.marketplace.ui.views.main.header;

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
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiPrimaryButton;
import org.ost.marketplace.ui.views.main.header.dialogs.LoginDialog;
import org.ost.marketplace.ui.views.main.header.dialogs.LogoutDialog;
import org.ost.marketplace.ui.views.main.header.dialogs.SignUpDialog;
import org.ost.marketplace.ui.views.main.header.settings.SettingsOverlay;

import static org.ost.marketplace.services.i18n.I18nKey.*;

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
    private final transient UiComponentFactory<UiPrimaryButton> primaryButtonFactory;

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
        authContextService.getCurrentUser().ifPresentOrElse(
                currentUser -> {
                    userInfo.setText(i18n.get(HEADER_SIGNED_IN, currentUser.email()));
                    authRow.add(userInfo, createSettingsButton(), createLogoutButton());
                },
                () -> {
                    userInfo.setText(i18n.get(HEADER_NOT_SIGNED_IN));
                    authRow.add(userInfo, createLoginButton(), createSignUpButton());
                });

        return authRow;
    }

    private UiPrimaryButton createSettingsButton() {
        UiPrimaryButton button = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(HEADER_SETTINGS).icon(VaadinIcon.COG.create()).build());
        button.addClickListener(_ -> settingsOverlay.openSettings());
        button.addClassName("header-settings-button");
        return button;
    }

    private UiPrimaryButton createLoginButton() {
        UiPrimaryButton loginButton = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(HEADER_LOGIN).icon(VaadinIcon.SIGN_IN.create()).build());
        loginButton.addClickListener(_ -> loginDialog.open());
        loginButton.addClassName("header-login-button");
        return loginButton;
    }

    private UiPrimaryButton createSignUpButton() {
        UiPrimaryButton signUpButton = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(HEADER_SIGNUP).icon(VaadinIcon.USER.create()).build());
        signUpButton.addClickListener(_ -> signUpDialog.open());
        signUpButton.addClassName("header-signup-button");
        return signUpButton;
    }

    private UiPrimaryButton createLogoutButton() {
        UiPrimaryButton logoutButton = primaryButtonFactory.build(
                UiPrimaryButton.Parameters.builder().labelKey(HEADER_LOGOUT).icon(VaadinIcon.SIGN_OUT.create()).build());
        logoutButton.addClickListener(_ -> logoutDialog.open());
        logoutButton.addClassName("header-logout-button");
        return logoutButton;
    }
}
