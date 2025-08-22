package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.Locale;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.SessionUtil;

@SpringComponent
@UIScope
public class HeaderBar extends HorizontalLayout {

	public HeaderBar(LocaleSelectorComponent localeSelectorComponent, LoginDialog loginDialog,
					 LogoutDialog logoutDialog, SignUpDialog signUpDialog, I18nService i18n) {
		setWidthFull();
		setPadding(true);
		setSpacing(true);
		setAlignItems(Alignment.CENTER);
		setJustifyContentMode(JustifyContentMode.END);

		VerticalLayout authBlock = new VerticalLayout();
		authBlock.setSpacing(false);
		authBlock.setPadding(false);
		authBlock.setAlignItems(Alignment.END);

		User currentUser = AuthUtil.getCurrentUser();

		Locale locale = SessionUtil.getCurrentLocale();

		Span userInfo = new Span();
		if (currentUser != null) {
			userInfo.setText(i18n.get("header.signedIn", locale, currentUser.getEmail()));
			authBlock.add(new HorizontalLayout(userInfo, localeSelectorComponent));
		} else {
			userInfo.setText(i18n.get("header.notSignedIn", locale));
			authBlock.add(new HorizontalLayout(userInfo));
		}

		if (currentUser != null) {
			Button logoutButton = new Button(i18n.get("header.logout", locale), VaadinIcon.SIGN_OUT.create(),
				e -> logoutDialog.open());
			authBlock.add(new HorizontalLayout(logoutButton));
		} else {
			Button loginButton = new Button(i18n.get("header.login", locale), VaadinIcon.SIGN_IN.create(),
				e -> loginDialog.open());
			Button signUpButton = new Button(i18n.get("header.signup", locale), VaadinIcon.USER.create(),
				e -> signUpDialog.open());
			authBlock.add(new HorizontalLayout(loginButton, signUpButton));
		}

		add(authBlock);
	}
}

