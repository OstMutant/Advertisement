package org.ost.advertisement.ui.views.header;

import static org.ost.advertisement.constans.I18nKey.*;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;
import org.ost.advertisement.ui.utils.SessionUtil;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;

@SpringComponent
@UIScope
public class LoginDialog extends Dialog {

	private final EmailField emailField;
	private final PasswordField passwordField;

	public LoginDialog(AuthService authService, I18nService i18n) {
		setModal(true);
		setDraggable(false);
		setResizable(false);
		setHeaderTitle(i18n.get(LOGIN_HEADER_TITLE));

		emailField = DialogContentFactory.emailField(i18n, LOGIN_EMAIL_LABEL, LOGIN_EMAIL_LABEL, true);
		passwordField = DialogContentFactory.passwordField(i18n, LOGIN_PASSWORD_LABEL, LOGIN_PASSWORD_LABEL, true);

		Button loginButton = DialogContentFactory.primaryButton(i18n, LOGIN_BUTTON_SUBMIT);
		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, LOGIN_BUTTON_CANCEL);

		loginButton.addClickListener(event -> handleLogin(authService, i18n));
		cancelButton.addClickListener(event -> close());

		HorizontalLayout actions = new HorizontalLayout(loginButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(JustifyContentMode.END);

		FormLayout form = new FormLayout(emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		add(new H2(i18n.get(LOGIN_WELCOME)), form);
	}

	private void handleLogin(AuthService authService, I18nService i18n) {
		boolean success = authService.login(emailField.getValue(), passwordField.getValue());

		if (success) {
			close();
			DialogContentFactory.showSuccess(i18n, LOGIN_SUCCESS);
			SessionUtil.refreshCurrentLocale();
			UI.getCurrent().getPage().reload();
		} else {
			DialogContentFactory.showError(i18n, LOGIN_ERROR, "");
		}
	}
}
