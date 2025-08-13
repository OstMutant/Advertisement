package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.ui.utils.SessionUtil;

@SpringComponent
@UIScope
public class HeaderBar extends HorizontalLayout {

	public HeaderBar(LoginDialog loginDialog, LogoutDialog logoutDialog, SignUpDialog signUpDialog) {
		setWidthFull();
		setPadding(true);
		setSpacing(true);
		setAlignItems(Alignment.CENTER);
		setJustifyContentMode(JustifyContentMode.END);

		User currentUser = SessionUtil.getCurrentUser();

		Span userInfo = new Span();
		Button loginButton = new Button("Log In", VaadinIcon.SIGN_IN.create(), e -> loginDialog.open());
		Button signUpButton = new Button("Sign Up", VaadinIcon.USER.create(), e -> signUpDialog.open());
		Button logoutButton = new Button("Log Out", VaadinIcon.SIGN_OUT.create(), e -> logoutDialog.open());

		if (currentUser != null) {
			userInfo.setText("Signed in as: " + currentUser.getEmail());
			add(userInfo, logoutButton);
		} else {
			userInfo.setText("Not signed in");
			add(userInfo, loginButton, signUpButton);
		}
	}
}

