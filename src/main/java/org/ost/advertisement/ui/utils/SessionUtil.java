package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.UI;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SessionUtil {

	public static Locale getCurrentLocale() {
		return getCurrentLocale(UI.getCurrent());
	}

	public static void refreshCurrentLocale() {
		refreshCurrentLocale(UI.getCurrent());
	}

	public static Locale getCurrentLocale(UI ui) {
		User currentUser = AuthUtil.getCurrentUser();
		return currentUser != null ? currentUser.getLocaleAsObject() : ui.getSession().getLocale();
	}

	public static void refreshCurrentLocale(UI ui) {
		ui.getSession().setLocale(getCurrentLocale(ui));
	}

}
