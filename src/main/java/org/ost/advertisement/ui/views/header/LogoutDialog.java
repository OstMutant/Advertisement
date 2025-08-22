package org.ost.advertisement.ui.views.header;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.AuthService;

@SpringComponent
@UIScope
public class LogoutDialog extends ConfirmDialog {

	public LogoutDialog(AuthService authService) {
		setHeader("Are you sure?");
		setText("Do you want to log out?");
		setConfirmButton("Yes", ed -> {
			UI ui = UI.getCurrent();
			authService.logout();
			close();
			ui.getPage().reload();
		});
		setCancelButton("Cancel", ed -> this.close());
	}
}
