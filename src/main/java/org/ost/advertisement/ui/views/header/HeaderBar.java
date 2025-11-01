package org.ost.advertisement.ui.views.header;

import static org.ost.advertisement.constants.I18nKey.HEADER_LOGIN;
import static org.ost.advertisement.constants.I18nKey.HEADER_LOGOUT;
import static org.ost.advertisement.constants.I18nKey.HEADER_NOT_SIGNED_IN;
import static org.ost.advertisement.constants.I18nKey.HEADER_SIGNED_IN;
import static org.ost.advertisement.constants.I18nKey.HEADER_SIGNUP;

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
		authBlock.add(new HorizontalLayout(localeSelectorComponent));

		HorizontalLayout authBlockRow = new HorizontalLayout();
		authBlockRow.setAlignItems(Alignment.CENTER);

		authBlock.add(authBlockRow);
		add(authBlock);

		Span userInfo = new Span();
		User currentUser = AuthUtil.getCurrentUser();
		if (currentUser != null) {
			userInfo.setText(i18n.get(HEADER_SIGNED_IN, currentUser.email()));
			Button logoutButton = new Button(i18n.get(HEADER_LOGOUT), VaadinIcon.SIGN_OUT.create(),
				e -> logoutDialog.open());
			authBlockRow.add(userInfo, logoutButton);
		} else {
			userInfo.setText(i18n.get(HEADER_NOT_SIGNED_IN));
			Button loginButton = new Button(i18n.get(HEADER_LOGIN), VaadinIcon.SIGN_IN.create(),
				e -> loginDialog.open());
			Button signUpButton = new Button(i18n.get(HEADER_SIGNUP), VaadinIcon.USER.create(),
				e -> signUpDialog.open());
			authBlockRow.add(userInfo, loginButton, signUpButton);
		}
	}
}


