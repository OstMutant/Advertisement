package org.ost.advertisement.ui.util;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import java.time.ZoneId;

public class TimeZoneUtil {

	public static void detectTimeZone() {
		UI.getCurrent().getPage()
			.executeJs("return Intl.DateTimeFormat().resolvedOptions().timeZone;")
			.then(String.class, timeZoneId -> VaadinSession.getCurrent().setAttribute("clientTimeZoneId", timeZoneId));
	}

	public static String getClientTimeZoneId() {
		String timeZoneId = (String) VaadinSession.getCurrent().getAttribute("clientTimeZoneId");
		if (timeZoneId == null) {
			return ZoneId.systemDefault().getId();
		}
		return timeZoneId;
	}
}
