package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.Locale;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.SessionUtil;

@SpringComponent
@UIScope
public class LogoutDialog extends ConfirmDialog {

	public LogoutDialog(AuthService authService, I18nService i18n) {
		setText(i18n.get("logout.confirm.text"));
		setConfirmButton(i18n.get("logout.confirm.yes"), ed -> {
			UI ui = UI.getCurrent();
			authService.logout();
			close();
			SessionUtil.refreshCurrentLocale(ui);
			ui.getPage().reload();
		});
		setCancelButton(i18n.get("logout.confirm.cancel"), ed -> this.close());
	}
}
