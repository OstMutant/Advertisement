package org.ost.marketplace.ui.views.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SupportUtil {

    /**
     * Parses a whole-number string into a Long. Returns null for blank input or text that
     * doesn't parse as a whole number (e.g. "123.99") -- callers relying on that distinction for
     * user feedback (e.g. a field's own invalid state) must check parseability separately.
     */
    public static Long toLongOrNull(String value) {
        String trimmed = nullIfBlank(value);
        if (trimmed == null) return null;
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static <T> boolean hasChanged(T current, T previous) {
        return !Objects.equals(current, previous);
    }

    /**
     * Returns null if the string is null or blank, otherwise returns the trimmed string.
     */
    public static String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
