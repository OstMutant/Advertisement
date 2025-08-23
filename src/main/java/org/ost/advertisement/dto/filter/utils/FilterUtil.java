package org.ost.advertisement.dto.filter.utils;

import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FilterUtil {

	public static Long toLong(Double value) {
		return value != null ? value.longValue() : null;
	}

	public static boolean isValidNumberRange(Long min, Long max) {
		if (isNotValidNumberValue(min)) {
			return false;
		}
		if (isNotValidNumberValue(max)) {
			return false;
		}
		return min == null || max == null || min <= max;
	}

	private static boolean isNotValidNumberValue(Long value) {
		return !isValidNumberValue(value);
	}

	private static boolean isValidNumberValue(Long value) {
		return value == null || value > 0;
	}

	public static boolean isValidDateRange(Instant start, Instant end) {
		return start == null || end == null || !start.isAfter(end);
	}

	public static <T> boolean hasNotChanged(T current, T previous) {
		return Objects.equals(current, previous);
	}

	public static <T> boolean hasChanged(T current, T previous) {
		return !hasNotChanged(current, previous);
	}
}
