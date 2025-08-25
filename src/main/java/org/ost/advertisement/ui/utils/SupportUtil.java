package org.ost.advertisement.ui.utils;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SupportUtil {

	public static Long toLong(Double value) {
		return value != null ? value.longValue() : null;
	}

	public static <T> boolean hasChanged(T current, T previous) {
		return !hasNotChanged(current, previous);
	}

	public static <T> boolean hasNotChanged(T current, T previous) {
		return Objects.equals(current, previous);
	}
}
