package org.ost.advertisement.ui.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public class FilterFieldsUtil {

	public static Instant toInstant(LocalDate date) {
		return date != null ? date.atStartOfDay(ZoneId.systemDefault()).toInstant() : null;
	}

	public static Long toLong(Double value) {
		return value != null ? value.longValue() : null;
	}

	public static boolean isValidNumberRange(Long min, Long max) {
		return min == null || max == null || min <= max;
	}

	public static boolean isValidDateRange(Instant start, Instant end) {
		return start == null || end == null || !start.isAfter(end);
	}

	public static <T> boolean hasChanged(T current, T original) {
		return !Objects.equals(current, original);
	}
}
