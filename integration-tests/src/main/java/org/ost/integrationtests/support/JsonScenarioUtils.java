package org.ost.integrationtests.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tiny regex-based JSON field extraction for Level 3 scenario tests -- avoids pulling a full JSON parser into assertions that only need one field. */
public final class JsonScenarioUtils {

    private static final Pattern ID_PATTERN = Pattern.compile("\"id\":(\\d+)");

    private JsonScenarioUtils() {
    }

    public static long extractId(String json) {
        Matcher matcher = ID_PATTERN.matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No id found in response: " + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    public static String extractStringField(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("No \"" + field + "\" field found in response: " + json);
        }
        return matcher.group(1);
    }
}
