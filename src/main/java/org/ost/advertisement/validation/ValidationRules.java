package org.ost.advertisement.validation;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidationRules {

	public static boolean isValidNumberRange(Long min, Long max) {
		if (isNotValidNumberValue(min)) {
			return false;
		}
		if (isNotValidNumberValue(max)) {
			return false;
		}
		return min == null || max == null || min <= max;
	}

	public static boolean isNotValidNumberRange(Long min, Long max) {
		return !isValidNumberRange(min, max);
	}

	public static boolean isValidDateRange(Instant start, Instant end) {
		return start == null || end == null || !start.isAfter(end);
	}

	public static boolean isNotValidDateRange(Instant start, Instant end) {
		return !isValidDateRange(start, end);
	}


	private static boolean isNotValidNumberValue(Long value) {
		return !isValidNumberValue(value);
	}

	private static boolean isValidNumberValue(Long value) {
		return value == null || value > 0;
	}

	public static boolean isTooLong(String value, int maxLength) {
		return value != null && value.length() > maxLength;
	}
}
