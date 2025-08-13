package org.ost.advertisement.ui.utils;

import com.vaadin.flow.server.VaadinSession;
import org.ost.advertisement.entities.User;

public class SessionUtil {

	public static User getCurrentUser() {
		return VaadinSession.getCurrent().getAttribute(User.class);
	}

	public static boolean isEmptyCurrentUser() {
		return SessionUtil.getCurrentUser() == null;
	}

	public static void setCurrentUser(User user) {
		VaadinSession.getCurrent().setAttribute(User.class, user);
	}

	public static void clearUser() {
		VaadinSession.getCurrent().setAttribute(User.class, null);
	}
}
