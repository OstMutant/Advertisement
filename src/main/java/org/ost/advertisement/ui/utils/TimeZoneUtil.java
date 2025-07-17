package org.ost.advertisement.ui.utils;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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

	public static String formatInstant(Instant instant) {
		if (instant == null) {
			return "N/A";
		}

		LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.of(getClientTimeZoneId()));
		return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
	}
}
