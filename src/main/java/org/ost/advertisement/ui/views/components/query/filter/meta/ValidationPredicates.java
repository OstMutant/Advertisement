package org.ost.advertisement.ui.views.components.query.filter.meta;

import java.util.function.BiPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.services.ValidationService;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValidationPredicates {

	public static <F> BiPredicate<ValidationService<F>, F> range(String start, String end) {
		return (v, dto) -> v.isValidProperty(dto, start) && v.isValidProperty(dto, end);
	}
}
