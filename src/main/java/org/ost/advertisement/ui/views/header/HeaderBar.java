package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.header.dialogs.LoginDialog;
import org.ost.advertisement.ui.views.header.dialogs.LogoutDialog;
import org.ost.advertisement.ui.views.header.dialogs.SignUpDialog;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
public class HeaderBar extends HorizontalLayout {

    public HeaderBar(LocaleSelectorComponent localeSelectorComponent,
                     LoginDialog loginDialog,
                     LogoutDialog logoutDialog,
                     SignUpDialog signUpDialog,
                     I18nService i18n) {

        addClassName("header-bar");

        VerticalLayout authBlock = createAuthBlock(localeSelectorComponent);
        HorizontalLayout authRow = createUserInfoRow(loginDialog, logoutDialog, signUpDialog, i18n);

        authBlock.add(authRow);
        add(authBlock);
    }

    private VerticalLayout createAuthBlock(LocaleSelectorComponent localeSelectorComponent) {
        VerticalLayout authBlock = new VerticalLayout();
        authBlock.addClassName("header-auth-block");

        HorizontalLayout localeRow = new HorizontalLayout(localeSelectorComponent);
        localeRow.addClassName("header-locale-row");

        authBlock.add(localeRow);
        return authBlock;
    }

    private HorizontalLayout createUserInfoRow(LoginDialog loginDialog,
                                               LogoutDialog logoutDialog,
                                               SignUpDialog signUpDialog,
                                               I18nService i18n) {
        HorizontalLayout authRow = new HorizontalLayout();
        authRow.addClassName("header-auth-row");

        Span userInfo = new Span();
        User currentUser = AuthUtil.getCurrentUser();

        if (currentUser != null) {
            userInfo.setText(i18n.get(HEADER_SIGNED_IN, currentUser.getEmail()));
            Button logoutButton = new Button(i18n.get(HEADER_LOGOUT), VaadinIcon.SIGN_OUT.create(),
                    _ -> logoutDialog.open());
            logoutButton.addClassName("header-logout-button");
            authRow.add(userInfo, logoutButton);
        } else {
            userInfo.setText(i18n.get(HEADER_NOT_SIGNED_IN));
            Button loginButton = new Button(i18n.get(HEADER_LOGIN), VaadinIcon.SIGN_IN.create(),
                    _ -> loginDialog.open());
            loginButton.addClassName("header-login-button");

            Button signUpButton = new Button(i18n.get(HEADER_SIGNUP), VaadinIcon.USER.create(),
                    _ -> signUpDialog.open());
            signUpButton.addClassName("header-signup-button");

            authRow.add(userInfo, loginButton, signUpButton);
        }

        return authRow;
    }
}
