package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;
import org.ost.advertisement.ui.utils.SessionUtil;

@SpringComponent
@UIScope
public class LogoutDialog extends ConfirmDialog {

	public LogoutDialog(AuthService authService) {
		setText("Do you want to log out?");
		setConfirmButton("Yes", ed -> {
			UI ui = UI.getCurrent();
			authService.logout();
			close();
			SessionUtil.refreshCurrentLocale(ui);
			ui.getPage().reload();
		});
		setCancelButton("Cancel", ed -> this.close());
	}
}
