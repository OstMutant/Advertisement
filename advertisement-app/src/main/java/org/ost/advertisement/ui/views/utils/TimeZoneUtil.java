package org.ost.advertisement.ui.views.utils;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeZoneUtil {

    public static void detectTimeZone() {
        UI.getCurrent().getPage()
                .executeJs("return Intl.DateTimeFormat().resolvedOptions().timeZone;")
                .then(String.class, timeZoneId -> VaadinSession.getCurrent().setAttribute("clientTimeZoneId", timeZoneId));
    }

    public static String getClientTimeZoneId() {
        String timeZoneId = (String) VaadinSession.getCurrent().getAttribute("clientTimeZoneId");
        return timeZoneId != null ? timeZoneId : ZoneId.systemDefault().getId();
    }

    public static String formatInstant(Instant instant) {
        return formatInstant(instant, "N/A");
    }

    public static String formatInstant(Instant instant, String valueIfNull) {
        if (instant == null) return valueIfNull;
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.of(getClientTimeZoneId()));
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static String formatInstantHuman(Instant instant) {
        return formatInstantHuman(instant, "N/A");
    }

    public static String formatInstantHuman(Instant instant, String valueIfNull) {
        if (instant == null) return valueIfNull;
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.of(getClientTimeZoneId()));
        Locale locale = getClientLocale();
        return dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withLocale(locale));
    }

    private static Locale getClientLocale() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null && session.getLocale() != null) {
            return session.getLocale();
        }
        return Locale.getDefault();
    }

    public static Instant toInstant(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneId.of(getClientTimeZoneId())).toInstant() : null;
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(ZoneId.of(getClientTimeZoneId())).toInstant() : null;
    }
}