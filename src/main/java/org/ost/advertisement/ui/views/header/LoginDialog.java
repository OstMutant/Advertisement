package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.utils.SessionUtil;

@SpringComponent
@UIScope
public class LoginDialog extends Dialog {

	public LoginDialog(AuthService authService, I18nService i18n) {
		setModal(true);
		setDraggable(false);
		setResizable(false);
		setHeaderTitle(i18n.get("login.header.title"));

		EmailField emailField = new EmailField(i18n.get("login.email.label"));
		PasswordField passwordField = new PasswordField(i18n.get("login.password.label"));

		Button loginButton = new Button(i18n.get("login.button.submit"));
		loginButton.addThemeName("primary");

		Button cancelButton = new Button(i18n.get("login.button.cancel"), e -> close());
		cancelButton.addThemeName("tertiary");

		HorizontalLayout actions = new HorizontalLayout(loginButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

		FormLayout form = new FormLayout(emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		add(new H2(i18n.get("login.welcome")), form);

		loginButton.addClickListener(event -> {
			boolean success = authService.login(emailField.getValue(), passwordField.getValue());

			if (success) {
				close();
				NotificationType.SUCCESS.show(i18n.get("login.success"));
				SessionUtil.refreshCurrentLocale();
				UI.getCurrent().getPage().reload();
			} else {
				NotificationType.ERROR.show(i18n.get("login.error"));
			}
		});
	}
}

