package org.ost.advertisement.ui.views.header;

import static org.ost.advertisement.constans.I18nKey.LOGIN_BUTTON_CANCEL;
import static org.ost.advertisement.constans.I18nKey.LOGIN_BUTTON_SUBMIT;
import static org.ost.advertisement.constans.I18nKey.LOGIN_EMAIL_LABEL;
import static org.ost.advertisement.constans.I18nKey.LOGIN_ERROR;
import static org.ost.advertisement.constans.I18nKey.LOGIN_HEADER_TITLE;
import static org.ost.advertisement.constans.I18nKey.LOGIN_PASSWORD_LABEL;
import static org.ost.advertisement.constans.I18nKey.LOGIN_SUCCESS;
import static org.ost.advertisement.constans.I18nKey.LOGIN_WELCOME;

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
		setHeaderTitle(i18n.get(LOGIN_HEADER_TITLE));

		EmailField emailField = new EmailField(i18n.get(LOGIN_EMAIL_LABEL));
		PasswordField passwordField = new PasswordField(i18n.get(LOGIN_PASSWORD_LABEL));

		Button loginButton = new Button(i18n.get(LOGIN_BUTTON_SUBMIT));
		loginButton.addThemeName("primary");

		Button cancelButton = new Button(i18n.get(LOGIN_BUTTON_CANCEL), e -> close());
		cancelButton.addThemeName("tertiary");

		HorizontalLayout actions = new HorizontalLayout(loginButton, cancelButton);
		actions.setSpacing(true);
		actions.setJustifyContentMode(HorizontalLayout.JustifyContentMode.END);

		FormLayout form = new FormLayout(emailField, passwordField, new Div(actions));
		form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

		add(new H2(i18n.get(LOGIN_WELCOME)), form);

		loginButton.addClickListener(event -> {
			boolean success = authService.login(emailField.getValue(), passwordField.getValue());

			if (success) {
				close();
				NotificationType.SUCCESS.show(i18n.get(LOGIN_SUCCESS));
				SessionUtil.refreshCurrentLocale();
				UI.getCurrent().getPage().reload();
			} else {
				NotificationType.ERROR.show(i18n.get(LOGIN_ERROR));
			}
		});
	}
}

