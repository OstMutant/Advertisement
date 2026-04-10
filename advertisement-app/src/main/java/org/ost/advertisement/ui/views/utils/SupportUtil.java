package org.ost.advertisement.ui.views.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Objects;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SupportUtil {

    public static Long toLong(Double value) {
        return value != null ? value.longValue() : null;
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
