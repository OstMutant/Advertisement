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

		Span userInfo = new Span();
		if (currentUser != null) {
			userInfo.setText(i18n.get("header.signedIn", currentUser.getEmail()));
			authBlock.add(new HorizontalLayout(userInfo, localeSelectorComponent));
		} else {
			userInfo.setText(i18n.get("header.notSignedIn"));
			authBlock.add(new HorizontalLayout(userInfo));
		}

		if (currentUser != null) {
			Button logoutButton = new Button(i18n.get("header.logout"), VaadinIcon.SIGN_OUT.create(),
				e -> logoutDialog.open());
			authBlock.add(new HorizontalLayout(logoutButton));
		} else {
			Button loginButton = new Button(i18n.get("header.login"), VaadinIcon.SIGN_IN.create(),
				e -> loginDialog.open());
			Button signUpButton = new Button(i18n.get("header.signup"), VaadinIcon.USER.create(),
				e -> signUpDialog.open());
			authBlock.add(new HorizontalLayout(loginButton, signUpButton));
		}

		add(authBlock);
	}
}

